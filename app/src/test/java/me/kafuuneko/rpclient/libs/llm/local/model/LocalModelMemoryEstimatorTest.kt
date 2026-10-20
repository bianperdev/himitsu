// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalModelMemoryEstimatorTest {
    private val gib = 1024L * 1024 * 1024

    @Test
    fun llama31With8kContextNeedsOneGibOfKvCache() {
        // 32 layers, 8 KV heads of 128 (4096 / 32), F16: the figure in the design document
        val shape = GgufAttentionShape(blockCount = 32, headCount = 32, headCountKv = 8, embeddingLength = 4096)

        assertEquals(gib, LocalModelMemoryEstimator.kvCacheBytes(shape, 8192))
    }

    @Test
    fun explicitKeyAndValueLengthsWinOverTheEmbeddingDivision() {
        val shape = GgufAttentionShape(
            blockCount = 2, headCount = 8, headCountKv = 2, embeddingLength = 1024, keyLength = 256, valueLength = 128
        )

        assertEquals(2L * 2 * (256 + 128) * 2 * 10, LocalModelMemoryEstimator.kvCacheBytes(shape, 10))
    }

    @Test
    fun missingShapeCountsNoKvCacheInsteadOfFailing() {
        assertEquals(0L, LocalModelMemoryEstimator.kvCacheBytes(GgufAttentionShape(), 4096))
        assertEquals(
            10L + LocalModelMemoryEstimator.OVERHEAD_BYTES,
            LocalModelMemoryEstimator.estimateBytes(10, GgufAttentionShape(), 4096)
        )
    }

    @Test
    fun fitUsesHalfAndSeventyPercentOfTotalRam() {
        val total = 12 * gib

        assertEquals(LocalModelMemoryFit.Comfortable, LocalModelMemoryEstimator.fit(6 * gib, total))
        assertEquals(LocalModelMemoryFit.Tight, LocalModelMemoryEstimator.fit(7 * gib, total))
        assertEquals(LocalModelMemoryFit.Risky, LocalModelMemoryEstimator.fit(9 * gib, total))
        assertEquals(LocalModelMemoryFit.TooLarge, LocalModelMemoryEstimator.fit(13 * gib, total))
    }
}
