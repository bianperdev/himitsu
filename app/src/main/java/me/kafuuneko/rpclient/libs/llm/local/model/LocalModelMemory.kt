// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local.model

/** How comfortably a model fits in the device's memory. */
enum class LocalModelMemoryFit {
    /** Uses up to half of the RAM. */
    Comfortable,

    /** Uses half to 70 % of the RAM: other apps may be closed. */
    Tight,

    /** Uses more than 70 % of the RAM: the system may kill Himitsu while it generates. */
    Risky,

    /** Needs more memory than the device has. */
    TooLarge
}

/**
 * Estimates how much memory a model needs while it generates.
 *
 * Estimate = file size (weights are memory-mapped) + KV cache + a fixed allowance for compute
 * buffers. The KV cache assumes F16 entries in every layer, which overestimates models with
 * sliding-window or linear attention; erring high is the safer side for a warning.
 */
object LocalModelMemoryEstimator {
    /** Compute buffers and runtime overhead measured on llama.cpp for 1-8B models, rounded up. */
    const val OVERHEAD_BYTES = 300L * 1024 * 1024
    private const val KV_BYTES_PER_ELEMENT = 2L

    /** Estimated bytes for a model of [fileBytes] with [shape] at [contextTokens]. */
    fun estimateBytes(fileBytes: Long, shape: GgufAttentionShape, contextTokens: Int): Long {
        return fileBytes + kvCacheBytes(shape, contextTokens) + OVERHEAD_BYTES
    }

    /** Bytes of the KV cache, or 0 when the header lacks the attention shape. */
    fun kvCacheBytes(shape: GgufAttentionShape, contextTokens: Int): Long {
        val heads = shape.headCount.takeIf { it > 0 } ?: return 0
        val keyLength = shape.keyLength.takeIf { it > 0 }
            ?: (shape.embeddingLength / heads).takeIf { it > 0 }
            ?: return 0
        val valueLength = shape.valueLength.takeIf { it > 0 } ?: keyLength
        val kvHeads = shape.headCountKv.takeIf { it > 0 } ?: heads
        val perToken = shape.blockCount.toLong() * kvHeads * (keyLength + valueLength) * KV_BYTES_PER_ELEMENT
        return perToken * contextTokens.coerceAtLeast(0)
    }

    /** Classifies [requiredBytes] against the device's total RAM. */
    fun fit(requiredBytes: Long, totalRamBytes: Long): LocalModelMemoryFit {
        if (totalRamBytes <= 0) return LocalModelMemoryFit.Comfortable
        val ratio = requiredBytes.toDouble() / totalRamBytes
        return when {
            ratio > 1.0 -> LocalModelMemoryFit.TooLarge
            ratio > RISKY_RATIO -> LocalModelMemoryFit.Risky
            ratio > TIGHT_RATIO -> LocalModelMemoryFit.Tight
            else -> LocalModelMemoryFit.Comfortable
        }
    }

    private const val TIGHT_RATIO = 0.5
    private const val RISKY_RATIO = 0.7
}
