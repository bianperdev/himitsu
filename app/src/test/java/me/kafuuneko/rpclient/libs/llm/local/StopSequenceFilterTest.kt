// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopSequenceFilterTest {
    @Test
    fun passesTextThroughWhenThereAreNoStopSequences() {
        val filter = StopSequenceFilter(emptyList())

        assertEquals("Hello", filter.accept("Hello"))
        assertEquals(" there", filter.accept(" there"))
        assertEquals("", filter.flush())
    }

    @Test
    fun dropsAStopSequenceThatSpansSeveralPieces() {
        val filter = StopSequenceFilter(listOf("\nUser:"))

        val shown = listOf("I smile.", "\nUs", "er:", " hi").joinToString("") { filter.accept(it) }

        assertEquals("I smile.", shown)
        assertTrue(filter.stopped)
        assertEquals("", filter.flush())
    }

    @Test
    fun releasesHeldTextWhenTheStopSequenceDoesNotComplete() {
        val filter = StopSequenceFilter(listOf("\nUser:"))

        assertEquals("Line", filter.accept("Line\nUs"))
        assertEquals("\nUsually", filter.accept("ually"))
        assertFalse(filter.stopped)
    }

    @Test
    fun flushReturnsAPartialMatchLeftAtTheEnd() {
        val filter = StopSequenceFilter(listOf("###"))

        assertEquals("Done", filter.accept("Done##"))
        assertEquals("##", filter.flush())
    }

    @Test
    fun stopsAtTheEarliestOfSeveralSequences() {
        val filter = StopSequenceFilter(listOf("END", "\n\n"))

        assertEquals("a", filter.accept("a\n\nbEND"))
        assertTrue(filter.stopped)
    }

    @Test
    fun ignoresEmptyStopSequences() {
        val filter = StopSequenceFilter(listOf(""))

        assertEquals("text", filter.accept("text"))
        assertFalse(filter.stopped)
    }
}
