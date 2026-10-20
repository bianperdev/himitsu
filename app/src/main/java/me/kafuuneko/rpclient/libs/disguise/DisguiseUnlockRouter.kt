// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise

import me.kafuuneko.rpclient.libs.security.lock.AuthOutcome

/** What a decoy does when its shortcut or recovery gesture is used. */
enum class ShortcutAction {
    /** Show the system authentication over the decoy. */
    ShowPrompt,

    /** Open the app gate screen, which handles storage failures and devices without a screen lock. */
    OpenGateScreen,

    /** Open the content: only reachable when nothing can be authenticated and the gate is already open. */
    OpenContent
}

/** What a decoy does once the system authentication has finished. */
enum class AfterAuthentication {
    /** Close the decoy that covers a Himitsu page, revealing it where the user left it. */
    RevealPage,

    /** Open Himitsu's main screen above the decoy that was launched from the launcher. */
    OpenHimitsu,

    /** Stay on the decoy, showing nothing of Himitsu. */
    StayInDecoy
}

/**
 * Decides where a decoy's shortcut leads. Pure, so every combination is unit-tested.
 *
 * - The shortcut asks for authentication every time, even when the app is still unlocked.
 * - Only a successful authentication opens content.
 * - A device without a screen lock cannot authenticate; the gate screen then warns and lets the
 *   user continue, as the app lock does, so a decoy never locks anyone out.
 */
object DisguiseUnlockRouter {
    /**
     * @param storageFailed the encrypted storage needs a decision on the gate screen
     * @param deviceSecure the device has a screen lock, so the system prompt can authenticate
     * @param gateOpen app content may be shown right now
     */
    fun onShortcut(storageFailed: Boolean, deviceSecure: Boolean, gateOpen: Boolean): ShortcutAction = when {
        storageFailed -> ShortcutAction.OpenGateScreen
        deviceSecure -> ShortcutAction.ShowPrompt
        gateOpen -> ShortcutAction.OpenContent
        else -> ShortcutAction.OpenGateScreen
    }

    /**
     * @param covering the decoy was opened over a Himitsu page because the app locked
     */
    fun afterAuthentication(outcome: AuthOutcome, covering: Boolean): AfterAuthentication = when (outcome) {
        AuthOutcome.Succeeded -> if (covering) AfterAuthentication.RevealPage else AfterAuthentication.OpenHimitsu
        AuthOutcome.Canceled, is AuthOutcome.Error -> AfterAuthentication.StayInDecoy
    }

    /** Where content opens without a prompt ([ShortcutAction.OpenContent]). */
    fun contentTarget(covering: Boolean): AfterAuthentication =
        if (covering) AfterAuthentication.RevealPage else AfterAuthentication.OpenHimitsu
}
