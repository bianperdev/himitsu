// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local.model

import android.content.Context
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** Progress of one catalog download, shared between the service and the screen. */
sealed class LocalModelDownloadState {
    data class Running(val downloadedBytes: Long, val totalBytes: Long) : LocalModelDownloadState()

    /** Hashing the finished file. */
    data object Verifying : LocalModelDownloadState()

    data class Failed(val failure: LocalModelDownloadFailure) : LocalModelDownloadState()
}

/** Why a download failed; mapped to a localized message by the UI. */
enum class LocalModelDownloadFailure {
    /** The connection failed or the server answered with an error; the download can resume. */
    Network,

    /** Not enough free space for the file. */
    NoSpace,

    /** The file did not match its published size or SHA-256 and was deleted. */
    Integrity,

    /** The file could not be written. */
    Storage
}

class LocalModelDownloadException(val failure: LocalModelDownloadFailure) :
    IllegalStateException("Local model download failed: $failure")

/** In-memory download states keyed by catalog entry; lost with the process, which is fine. */
class LocalModelDownloadTracker {
    private val mStates = MutableStateFlow<Map<String, LocalModelDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, LocalModelDownloadState>> = mStates.asStateFlow()

    fun set(key: String, state: LocalModelDownloadState?) {
        mStates.update { current -> if (state == null) current - key else current + (key to state) }
    }

    /** True while any download is running or verifying. */
    val isBusy: Boolean
        get() = mStates.value.values.any { it !is LocalModelDownloadState.Failed }
}

/**
 * Downloads a catalog model on the user's explicit request.
 *
 * - The only network destination is the pinned Hugging Face URL of the entry (and the CDN it
 *   redirects to). No token, cookie or device information is sent.
 * - A partial file survives cancellation by the system and resumes with an HTTP Range request;
 *   the bytes already on disk are hashed again first, so the final SHA-256 covers the whole file.
 * - The file is accepted only when its size and SHA-256 match the catalog; otherwise it is deleted.
 */
class LocalModelDownloader(
    private val mContext: Context,
    okHttpClient: OkHttpClient,
    private val mFiles: LocalModelFiles,
    private val mDao: LocalModelDao
) {
    // No redirects to plain HTTP and no cookies; the shared client already has no cookie jar
    private val mClient = okHttpClient.newBuilder().followSslRedirects(false).build()

    /** Bytes of a previous attempt that a new download would resume from. */
    fun partialBytes(entry: LocalModelCatalogEntry): Long = mFiles.downloadFileFor(entry.key).length()

    /** Deletes the partial file of a cancelled download. */
    fun discardPartial(entry: LocalModelCatalogEntry) {
        mFiles.downloadFileFor(entry.key).delete()
    }

    /**
     * Downloads [entry], verifies it and registers it.
     *
     * @throws LocalModelDownloadException on failure; a [LocalModelDownloadFailure.Network] failure keeps
     * the partial file for a later resume.
     */
    suspend fun download(
        entry: LocalModelCatalogEntry,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        onVerifying: () -> Unit
    ): LocalModelEntity = withContext(Dispatchers.IO) {
        mFiles.directory.mkdirs()
        val partial = mFiles.downloadFileFor(entry.key)
        if (partial.length() > entry.sizeBytes) partial.delete()
        val needed = entry.sizeBytes - partial.length() + FREE_SPACE_MARGIN_BYTES
        if (needed > allocatableBytes()) throw LocalModelDownloadException(LocalModelDownloadFailure.NoSpace)

        // Hash what is already on disk, then fetch the rest
        val digest = MessageDigest.getInstance("SHA-256")
        hashInto(partial, digest)
        if (partial.length() < entry.sizeBytes) fetch(entry, partial, digest, onProgress)

        // Verify size and hash before the file is ever handed to the native parser
        onVerifying()
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (partial.length() != entry.sizeBytes || hash != entry.sha256) {
            partial.delete()
            throw LocalModelDownloadException(LocalModelDownloadFailure.Integrity)
        }
        val metadata = try {
            partial.inputStream().use { GgufHeaderReader.read(it, partial.length()) }
        } catch (_: InvalidGgufException) {
            partial.delete()
            throw LocalModelDownloadException(LocalModelDownloadFailure.Integrity)
        }

        // Register under a random id, like an import
        val id = UUID.randomUUID().toString()
        if (!partial.renameTo(mFiles.fileFor(id))) throw LocalModelDownloadException(LocalModelDownloadFailure.Storage)
        val model = LocalModelEntity(
            id = id,
            displayName = entry.displayName,
            sizeBytes = entry.sizeBytes,
            sha256 = hash,
            architecture = metadata.architecture.orEmpty(),
            trainedContextTokens = metadata.trainedContextTokens,
            hasChatTemplate = metadata.hasChatTemplate,
            fileType = metadata.fileType,
            source = LocalModelSource.Downloaded,
            createTime = System.currentTimeMillis()
        )
        mDao.insert(model)
        model
    }

    /** Streams the missing bytes of [entry] into [partial], resuming when the server allows it. */
    private suspend fun fetch(
        entry: LocalModelCatalogEntry,
        partial: File,
        digest: MessageDigest,
        onProgress: (Long, Long) -> Unit
    ) {
        val offset = partial.length()
        val request = Request.Builder()
            .url(entry.downloadUrl)
            .apply { if (offset > 0) header("Range", "bytes=$offset-") }
            .build()
        val call = mClient.newCall(request)
        val job = currentCoroutineContext()[Job]
        val cancelHandle = job?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                // 206 continues the partial file; 200 means the server ignored Range, so start over
                val append = when (response.code) {
                    HTTP_PARTIAL_CONTENT -> true
                    HTTP_OK -> false
                    else -> throw LocalModelDownloadException(LocalModelDownloadFailure.Network)
                }
                if (!append) digest.reset()
                val body = response.body ?: throw LocalModelDownloadException(LocalModelDownloadFailure.Network)
                var downloaded = if (append) offset else 0L
                FileOutputStream(partial, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            if (downloaded > entry.sizeBytes) {
                                throw LocalModelDownloadException(LocalModelDownloadFailure.Integrity)
                            }
                            onProgress(downloaded, entry.sizeBytes)
                        }
                    }
                }
            }
        } catch (error: LocalModelDownloadException) {
            if (error.failure == LocalModelDownloadFailure.Integrity) partial.delete()
            throw error
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            val failure = if (allocatableBytes() < BUFFER_BYTES) {
                LocalModelDownloadFailure.NoSpace
            } else {
                LocalModelDownloadFailure.Network
            }
            throw LocalModelDownloadException(failure)
        } finally {
            cancelHandle?.dispose()
        }
    }

    private suspend fun hashInto(file: File, digest: MessageDigest) {
        if (!file.isFile) return
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
    }

    private fun allocatableBytes(): Long {
        val storage = mContext.getSystemService(StorageManager::class.java)
        return runCatching {
            storage.getAllocatableBytes(storage.getUuidForPath(mFiles.directory))
        }.getOrDefault(mFiles.directory.usableSpace)
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_PARTIAL_CONTENT = 206
        const val BUFFER_BYTES = 1024 * 1024
        const val FREE_SPACE_MARGIN_BYTES = 512L * 1024 * 1024
    }
}
