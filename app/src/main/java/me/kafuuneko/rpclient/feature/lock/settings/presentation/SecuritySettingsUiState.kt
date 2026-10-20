// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock.settings.presentation

import me.kafuuneko.rpclient.libs.security.lock.LockTimeout

/** State of the security settings screen. */
sealed class SecuritySettingsUiState {
    data object None : SecuritySettingsUiState()

    /**
     * @property deviceSecure whether the device has a screen lock, which the app lock needs
     */
    data class Normal(
        val appLockEnabled: Boolean,
        val deviceSecure: Boolean,
        val lockTimeout: LockTimeout,
        val hasRecoveryCode: Boolean,
        val isWorking: Boolean = false,
        val dialogState: SecuritySettingsDialogState = SecuritySettingsDialogState.None
    ) : SecuritySettingsUiState()

    data class Finished(val previous: SecuritySettingsUiState) : SecuritySettingsUiState()
}

/** Dialogs of the security settings screen. */
sealed class SecuritySettingsDialogState {
    data object None : SecuritySettingsDialogState()
    data object TimeoutPicker : SecuritySettingsDialogState()
    data object ConfirmReplaceRecoveryCode : SecuritySettingsDialogState()
    data object ConfirmRemoveRecoveryCode : SecuritySettingsDialogState()

    /**
     * Shows a new recovery code once. It is dropped from state as soon as the dialog closes,
     * and `toString` never prints it.
     */
    class ShowRecoveryCode(val code: String) : SecuritySettingsDialogState() {
        override fun toString(): String = "ShowRecoveryCode([REDACTED])"
    }
}
