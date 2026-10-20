// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock.settings.presentation

import android.net.Uri
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
    data class ShowSetAsideActions(val suffix: String) : SecuritySettingsUiIntent()
    data object ExportSetAside : SecuritySettingsUiIntent()

    /** The destination chosen in the system file picker, or null when the user canceled it. */
    data class ExportSetAsideTo(val destination: Uri?) : SecuritySettingsUiIntent()
    data object ShowDeleteSetAside : SecuritySettingsUiIntent()
    data class SetDeleteUnderstood(val understood: Boolean) : SecuritySettingsUiIntent()
    data object ConfirmDeleteSetAside : SecuritySettingsUiIntent()

    /** Result of the confirmation asked before a sensitive change; [confirmed] only after success. */
    data class ConfirmationFinished(val confirmed: Boolean) : SecuritySettingsUiIntent()
}
