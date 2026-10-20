// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise.secret

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptLimiterTest {
    private val now = 1_000_000L

    private fun failTimes(count: Int): AttemptState =
        (1..count).fold(AttemptState()) { state, _ -> AttemptLimiter.afterFailure(state, now) }

    @Test
    fun theFirstFourWrongAttemptsAreFree() {
        val state = failTimes(AttemptLimiter.FREE_ATTEMPTS - 1)
        assertFalse(AttemptLimiter.isBlocked(state, now))
    }

    @Test
    fun waitsGrowAndStopAtOneHour() {
        val waits = (5..10).map { failTimes(it).blockedUntil - now }
        assertEquals(listOf(30_000L, 60_000L, 300_000L, 900_000L, 3_600_000L, 3_600_000L), waits)
    }

    @Test
    fun aBlockEndsWhenItsWaitIsOver() {
        val state = failTimes(AttemptLimiter.FREE_ATTEMPTS)
        assertTrue(AttemptLimiter.isBlocked(state, now + 29_999L))
        assertFalse(AttemptLimiter.isBlocked(state, now + 30_000L))
    }
}
