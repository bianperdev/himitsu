// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local.model

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.llm.local.LocalModelRuntime

/** A stored model with what the models screen shows about it. */
data class LocalModelDetails(
    val entity: LocalModelEntity,
    /** Estimated memory while generating at [LocalModelLibrary.ESTIMATE_CONTEXT_TOKENS]. */
    val estimatedRamBytes: Long,
    val memoryFit: LocalModelMemoryFit
)

/**
 * Stored models as the models screen sees them: listing with memory estimates, deletion, and
 * which catalog entries are already on the device.
 */
class LocalModelLibrary(
    private val mContext: Context,
    private val mDao: LocalModelDao,
    private val mFiles: LocalModelFiles,
    private val mRuntime: LocalModelRuntime
) {
    /** Total RAM of the device, the reference for memory warnings. */
    val totalRamBytes: Long by lazy {
        val info = ActivityManager.MemoryInfo()
        mContext.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        info.totalMem
    }

    /** Stored models, newest first. Rows whose file disappeared are skipped. */
    suspend fun list(): List<LocalModelDetails> = withContext(Dispatchers.IO) {
        mDao.getAll().mapNotNull { entity ->
            val file = mFiles.fileFor(entity.id).takeIf { it.isFile } ?: return@mapNotNull null
            // Reading only the header is fast; it gives the shape the estimate needs
            val shape = runCatching { file.inputStream().use { GgufHeaderReader.read(it) }.attention }
                .getOrDefault(GgufAttentionShape())
            val estimate = LocalModelMemoryEstimator.estimateBytes(entity.sizeBytes, shape, ESTIMATE_CONTEXT_TOKENS)
            LocalModelDetails(entity, estimate, LocalModelMemoryEstimator.fit(estimate, totalRamBytes))
        }
    }

    /** Keys of catalog entries whose exact file (same SHA-256) is already stored. */
    suspend fun storedCatalogKeys(): Set<String> = withContext(Dispatchers.IO) {
        val hashes = mDao.getAll().map { it.sha256 }.toSet()
        LocalModelCatalog.entries.filter { it.sha256 in hashes }.map { it.key }.toSet()
    }

    /** Unloads any model from memory, then deletes [id]'s file and registry entry. */
    suspend fun delete(id: String) {
        mRuntime.release()
        withContext(Dispatchers.IO) {
            mFiles.fileFor(id).delete()
            mDao.deleteById(id)
        }
    }

    /** Display name of [id], or null when it is not a stored model. */
    suspend fun displayName(id: String): String? = withContext(Dispatchers.IO) { mDao.getById(id)?.displayName }

    companion object {
        /** Context assumed by estimates: the local preset's default. */
        const val ESTIMATE_CONTEXT_TOKENS = 4096
    }
}

/** Short label of a llama.cpp quantization id (`general.file_type`), or null when unknown. */
fun ggufFileTypeLabel(fileType: Int): String? = FILE_TYPE_LABELS[fileType]

private val FILE_TYPE_LABELS = mapOf(
    0 to "F32", 1 to "F16", 2 to "Q4_0", 3 to "Q4_1", 7 to "Q8_0", 8 to "Q5_0", 9 to "Q5_1",
    10 to "Q2_K", 11 to "Q3_K_S", 12 to "Q3_K_M", 13 to "Q3_K_L", 14 to "Q4_K_S", 15 to "Q4_K_M",
    16 to "Q5_K_S", 17 to "Q5_K_M", 18 to "Q6_K", 19 to "IQ2_XXS", 20 to "IQ2_XS", 21 to "Q2_K_S",
    22 to "IQ3_XS", 23 to "IQ3_XXS", 24 to "IQ1_S", 25 to "IQ4_NL", 26 to "IQ3_S", 27 to "IQ3_M",
    28 to "IQ2_S", 29 to "IQ2_M", 30 to "IQ4_XS", 31 to "IQ1_M", 32 to "BF16", 36 to "TQ1_0",
    37 to "TQ2_0", 38 to "MXFP4", 39 to "NVFP4", 40 to "Q1_0", 41 to "Q2_0"
)
