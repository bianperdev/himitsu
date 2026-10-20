// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.lock

/**
 * Lets other features make the app lock stricter, never looser.
 *
 * The default binding changes nothing. `feat/disguise` replaces it, so a decoy's launcher icon
 * or recents card never reopens a Himitsu page while the configured timeout is still running.
 */
fun interface LockPolicy {
    /** Whether leaving the app must lock it at once, whatever [LockTimeout] the user chose. */
    fun lockOnLeave(): Boolean

    companion object {
        /** Follows the user's timeout. */
        val Default = LockPolicy { false }
    }
}
