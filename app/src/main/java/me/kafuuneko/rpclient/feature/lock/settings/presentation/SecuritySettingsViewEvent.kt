// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock.settings.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** One-time actions the security settings host must perform. */
sealed class SecuritySettingsViewEvent : IViewEvent {
    /** Apply changed screen privacy settings to the current window at once. */
    data object ApplyWindowProtection : SecuritySettingsViewEvent()
}
