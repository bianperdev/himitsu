// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.settings.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** One-time actions of the disguise settings host. */
sealed class DisguiseSettingsViewEvent : IViewEvent {
    data object OpenSecuritySettings : DisguiseSettingsViewEvent()

    /** Ask for biometrics or the device credential before changing or turning off an active disguise. */
    data object RequestCredential : DisguiseSettingsViewEvent()

    /**
     * The launcher icon changed: close every Himitsu screen, as the warning said, so the user
     * picks the new icon from the launcher.
     */
    data object CloseApp : DisguiseSettingsViewEvent()
}
