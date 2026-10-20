// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise.secret

import kotlin.math.min

/**
 * Consecutive wrong shortcut attempts.
 *
 * @property blockedUntil wall-clock time until which attempts are ignored
 */
data class AttemptState(val failures: Int = 0, val blockedUntil: Long = 0L)

/**
 * Limits shortcut guesses without revealing that a shortcut exists.
 *
 * Only inputs shaped like the shortcut count. After [FREE_ATTEMPTS] wrong ones in a row,
 * attempts are silently ignored for a growing wait. A wrong attempt already looks like normal
 * decoy input, so the wait is invisible; the owner can still use the recovery gesture, which
 * is not limited because it only opens the system authentication.
 *
 * The wait uses the wall clock: changing the system time shortens it. That is acceptable for a
 * discretion secret.
 */
object AttemptLimiter {
    const val FREE_ATTEMPTS = 5

    /** Waits after the 5th, 6th, 7th, 8th and later wrong attempts. */
    private val WAITS_MILLIS = longArrayOf(30_000L, 60_000L, 300_000L, 900_000L, 3_600_000L)

    fun isBlocked(state: AttemptState, now: Long): Boolean = now < state.blockedUntil

    fun afterFailure(state: AttemptState, now: Long): AttemptState {
        val failures = state.failures + 1
        if (failures < FREE_ATTEMPTS) return AttemptState(failures)
        val wait = WAITS_MILLIS[min(failures - FREE_ATTEMPTS, WAITS_MILLIS.lastIndex)]
        return AttemptState(failures, now + wait)
    }
}
