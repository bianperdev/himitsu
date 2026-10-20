// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local.model

import android.content.Context
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** Where model files live: `noBackupFilesDir/models`, never included in Android backups. */
class LocalModelFiles(context: Context) {
    val directory: File = File(context.noBackupFilesDir, DIRECTORY_NAME)

    /** The stored file of model [id]. */
    fun fileFor(id: String): File = File(directory, "$id$EXTENSION")

    /** Temporary file used while model [id] is being copied. */
    fun partialFileFor(id: String): File = File(directory, "$id$EXTENSION$PARTIAL_SUFFIX")

    /** Deletes copies interrupted by a crash or a killed process. Call only when no import runs. */
    fun deletePartialFiles() {
        directory.listFiles { file -> file.name.endsWith(PARTIAL_SUFFIX) }?.forEach { it.delete() }
    }

    private companion object {
        const val DIRECTORY_NAME = "models"
        const val EXTENSION = ".gguf"
        const val PARTIAL_SUFFIX = ".part"
    }
}

/** Why an import failed; mapped to a localized message by the UI. */
enum class LocalModelImportFailure {
    /** The file is not a GGUF model. */
    NotGguf,

    /** The file could not be read or written. */
    Unreadable,

    /** There is not enough free space for a copy. */
    NoSpace
}

class LocalModelImportException(val failure: LocalModelImportFailure) :
    IllegalStateException("Local model import failed: $failure")

/**
 * Copies a GGUF file chosen with the Storage Access Framework into app storage.
 *
 * - The header is validated before copying, so a wrong file fails fast and costs no space.
 * - The copy goes to a partial file and is renamed only when complete; its SHA-256 is computed
 *   on the fly and stored, so later downloads and imports can be verified against it.
 * - The URI is used once and no persistent permission is requested.
 */
class LocalModelImporter(
    private val mContext: Context,
    private val mFiles: LocalModelFiles,
    private val mDao: LocalModelDao
) {
    /**
     * Imports the model at [uri] and returns its registry entry.
     *
     * @param onProgress called with copied and total bytes; total is 0 when the provider does not
     * report a size.
     * @throws LocalModelImportException when the file is not a GGUF model, cannot be read or does not fit.
     */
    suspend fun import(
        uri: Uri,
        onProgress: (copiedBytes: Long, totalBytes: Long) -> Unit
    ): LocalModelEntity = withContext(Dispatchers.IO) {
        val resolver = mContext.contentResolver
        val (fileName, totalBytes) = queryNameAndSize(uri)

        // Validate the header before using any space
        val metadata = try {
            resolver.openInputStream(uri)?.use { GgufHeaderReader.read(it) }
                ?: throw LocalModelImportException(LocalModelImportFailure.Unreadable)
        } catch (_: InvalidGgufException) {
            throw LocalModelImportException(LocalModelImportFailure.NotGguf)
        } catch (_: IOException) {
            throw LocalModelImportException(LocalModelImportFailure.Unreadable)
        }
        mFiles.directory.mkdirs()
        if (totalBytes > 0 && totalBytes + FREE_SPACE_MARGIN_BYTES > allocatableBytes()) {
            throw LocalModelImportException(LocalModelImportFailure.NoSpace)
        }

        // Copy to a partial file while hashing; any failure or cancellation deletes it
        val id = UUID.randomUUID().toString()
        val partial = mFiles.partialFileFor(id)
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        try {
            val input = resolver.openInputStream(uri)
                ?: throw LocalModelImportException(LocalModelImportFailure.Unreadable)
            input.use { source ->
                partial.outputStream().use { target ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        target.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        copied += read
                        onProgress(copied, totalBytes)
                    }
                }
            }
            if (!partial.renameTo(mFiles.fileFor(id))) throw IOException("Rename failed")
        } catch (error: Throwable) {
            partial.delete()
            throw when (error) {
                is LocalModelImportException -> error
                is IOException -> if (allocatableBytes() < COPY_BUFFER_BYTES) {
                    LocalModelImportException(LocalModelImportFailure.NoSpace)
                } else {
                    LocalModelImportException(LocalModelImportFailure.Unreadable)
                }
                else -> error
            }
        }

        // Register the model
        val entity = LocalModelEntity(
            id = id,
            displayName = metadata.name ?: fileName.removeSuffix(".gguf").ifBlank { id },
            sizeBytes = copied,
            sha256 = digest.digest().joinToString("") { "%02x".format(it) },
            architecture = metadata.architecture.orEmpty(),
            trainedContextTokens = metadata.trainedContextTokens,
            hasChatTemplate = metadata.hasChatTemplate,
            fileType = metadata.fileType,
            source = LocalModelSource.Imported,
            createTime = System.currentTimeMillis()
        )
        try {
            mDao.insert(entity)
        } catch (error: Throwable) {
            mFiles.fileFor(id).delete()
            throw error
        }
        entity
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long> {
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        mContext.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0).orEmpty()
                val size = if (cursor.isNull(1)) 0L else cursor.getLong(1)
                return name to size
            }
        }
        return "" to 0L
    }

    private fun allocatableBytes(): Long {
        val storage = mContext.getSystemService(StorageManager::class.java)
        return runCatching {
            storage.getAllocatableBytes(storage.getUuidForPath(mFiles.directory))
        }.getOrDefault(mFiles.directory.usableSpace)
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 1024 * 1024
        const val FREE_SPACE_MARGIN_BYTES = 512L * 1024 * 1024
    }
}
