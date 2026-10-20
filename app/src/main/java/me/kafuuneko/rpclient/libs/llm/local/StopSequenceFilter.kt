// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

/**
 * Applies stop sequences to streamed text.
 *
 * - Text that could be the start of a stop sequence is held back until it is clear whether the
 *   sequence completes, so a stop sequence never reaches the chat even when it spans pieces.
 * - Once a stop sequence matches, everything from it onwards is dropped and [stopped] turns true.
 */
class StopSequenceFilter(stops: List<String>) {
    private val mStops = stops.filter { it.isNotEmpty() }
    private val mPending = StringBuilder()

    /** True once a stop sequence matched; the caller should end generation. */
    var stopped: Boolean = false
        private set

    /** Adds a piece and returns the text that is safe to show now. */
    fun accept(piece: String): String {
        if (stopped) return ""
        mPending.append(piece)
        val text = mPending.toString()
        val match = mStops.mapNotNull { stop -> text.indexOf(stop).takeIf { it >= 0 } }.minOrNull()
        if (match != null) {
            stopped = true
            mPending.clear()
            return text.substring(0, match)
        }
        val held = mStops.maxOfOrNull { longestPartialMatch(text, it) } ?: 0
        val ready = text.substring(0, text.length - held)
        mPending.delete(0, ready.length)
        return ready
    }

    /** Returns the text still held back when generation ends without a match. */
    fun flush(): String {
        if (stopped) return ""
        return mPending.toString().also { mPending.clear() }
    }

    /** Length of the longest end of [text] that is a strict beginning of [stop]. */
    private fun longestPartialMatch(text: String, stop: String): Int {
        for (length in minOf(text.length, stop.length - 1) downTo 1) {
            if (text.regionMatches(text.length - length, stop, 0, length)) return length
        }
        return 0
    }
}
