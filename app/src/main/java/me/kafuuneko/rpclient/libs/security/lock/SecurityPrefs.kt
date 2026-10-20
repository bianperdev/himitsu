// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.lock

import com.chibatching.kotpref.KotprefModel

/** How long the app may stay in the background before it locks again. */
enum class LockTimeout(val millis: Long) {
    Immediately(0L),
    OneMinute(60_000L),
    FiveMinutes(300_000L);

    companion object {
        fun fromName(name: String): LockTimeout = entries.firstOrNull { it.name == name } ?: Immediately
    }
}

/**
 * Non-sensitive security settings of the `feat/security` track.
 *
 * Only switches live here; no key, code or secret. Changing them requires an unlocked app, and
 * an attacker able to edit this file already controls the device.
 */
object SecurityPrefs : KotprefModel() {
    override val kotprefName: String = "himitsu_security"

    /** Whether the app lock is on. Off by default. */
    var appLockEnabled by booleanPref(default = false)

    /** Name of the [LockTimeout] in use. */
    var lockTimeout by stringPref(default = LockTimeout.Immediately.name)

    /** Whether screens set `FLAG_SECURE` (no screenshots, blank recents thumbnail). */
    var secureScreenEnabled by booleanPref(default = false)
}
