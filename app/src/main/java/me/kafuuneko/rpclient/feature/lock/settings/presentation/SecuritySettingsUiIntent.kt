// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock.settings.presentation

import me.kafuuneko.rpclient.libs.security.lock.LockTimeout

/** User actions on the security settings screen. */
sealed class SecuritySettingsUiIntent {
    data object Init : SecuritySettingsUiIntent()
    data object Resume : SecuritySettingsUiIntent()
    data object Back : SecuritySettingsUiIntent()
    data class ToggleAppLock(val enabled: Boolean) : SecuritySettingsUiIntent()
    data class ToggleSecureScreen(val enabled: Boolean) : SecuritySettingsUiIntent()
    data object ShowTimeoutPicker : SecuritySettingsUiIntent()
    data class SelectTimeout(val timeout: LockTimeout) : SecuritySettingsUiIntent()
    data object CreateRecoveryCode : SecuritySettingsUiIntent()
    data object ConfirmReplaceRecoveryCode : SecuritySettingsUiIntent()
    data object ShowRemoveRecoveryCodeDialog : SecuritySettingsUiIntent()
    data object ConfirmRemoveRecoveryCode : SecuritySettingsUiIntent()
    data object DismissDialog : SecuritySettingsUiIntent()

    /** Result of the confirmation asked before a sensitive change; [confirmed] only after success. */
    data class ConfirmationFinished(val confirmed: Boolean) : SecuritySettingsUiIntent()
}
