// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise

import me.kafuuneko.rpclient.libs.security.lock.AuthOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Proves that no path from a decoy shortcut reaches content without authentication. */
class DisguiseUnlockRouterTest {
    private val booleans = listOf(false, true)
    private val failures = listOf(AuthOutcome.Canceled, AuthOutcome.Error("Too many attempts"))

    @Test
    fun aDeviceWithAScreenLockAlwaysAuthenticatesEvenWhenAlreadyUnlocked() {
        for (gateOpen in booleans) {
            assertEquals(ShortcutAction.ShowPrompt, DisguiseUnlockRouter.onShortcut(storageFailed = false, deviceSecure = true, gateOpen = gateOpen))
        }
    }

    @Test
    fun contentWithoutAPromptNeedsADeviceWithoutLockAndAnOpenGate() {
        for (storageFailed in booleans) for (deviceSecure in booleans) for (gateOpen in booleans) {
            val action = DisguiseUnlockRouter.onShortcut(storageFailed, deviceSecure, gateOpen)
            if (storageFailed || deviceSecure || !gateOpen) {
                assertNotEquals("$storageFailed $deviceSecure $gateOpen", ShortcutAction.OpenContent, action)
            }
        }
    }

    @Test
    fun storageFailuresGoToTheGateScreen() {
        for (deviceSecure in booleans) for (gateOpen in booleans) {
            assertEquals(ShortcutAction.OpenGateScreen, DisguiseUnlockRouter.onShortcut(storageFailed = true, deviceSecure = deviceSecure, gateOpen = gateOpen))
        }
    }

    @Test
    fun onlySuccessLeavesTheDecoy() {
        for (covering in booleans) {
            for (outcome in failures) {
                assertEquals(AfterAuthentication.StayInDecoy, DisguiseUnlockRouter.afterAuthentication(outcome, covering))
            }
        }
        assertEquals(AfterAuthentication.RevealPage, DisguiseUnlockRouter.afterAuthentication(AuthOutcome.Succeeded, covering = true))
        assertEquals(AfterAuthentication.OpenHimitsu, DisguiseUnlockRouter.afterAuthentication(AuthOutcome.Succeeded, covering = false))
    }
}
