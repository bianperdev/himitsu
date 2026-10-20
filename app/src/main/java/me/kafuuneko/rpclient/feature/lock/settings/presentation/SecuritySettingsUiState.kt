// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock.settings.presentation

import me.kafuuneko.rpclient.libs.security.lock.LockTimeout
import me.kafuuneko.rpclient.libs.security.storage.SetAsideKind

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
        val secureScreenEnabled: Boolean,
        val setAside: List<SetAsideItem> = emptyList(),
        val isWorking: Boolean = false,
        val dialogState: SecuritySettingsDialogState = SecuritySettingsDialogState.None
    ) : SecuritySettingsUiState()

    data class Finished(val previous: SecuritySettingsUiState) : SecuritySettingsUiState()
}

/**
 * A set of files moved aside, as listed on the screen.
 *
 * @property suffix identifies the set; it holds only its kind and time, never content
 */
data class SetAsideItem(
    val suffix: String,
    val kind: SetAsideKind,
    val time: Long?,
    val sizeBytes: Long,
    val canRestore: Boolean = false
)

/** Why a recovery code did not restore a copy. */
enum class RestoreCodeError { Malformed, WrongCode }

/** Dialogs of the security settings screen. */
sealed class SecuritySettingsDialogState {
    data object None : SecuritySettingsDialogState()
    data object TimeoutPicker : SecuritySettingsDialogState()
    data object ConfirmReplaceRecoveryCode : SecuritySettingsDialogState()
    data object ConfirmRemoveRecoveryCode : SecuritySettingsDialogState()
    data class SetAsideActions(val item: SetAsideItem) : SecuritySettingsDialogState()

    data class ConfirmRestoreSetAside(val item: SetAsideItem) : SecuritySettingsDialogState()
    data class ConfirmExportSetAside(val item: SetAsideItem) : SecuritySettingsDialogState()

    /** The copy's own key is gone; its recovery code is asked. The code is never kept here. */
    data class RestoreCodeInput(
        val item: SetAsideItem,
        val isWorking: Boolean = false,
        val error: RestoreCodeError? = null
    ) : SecuritySettingsDialogState()

    /** Deleting needs the box ticked first, then the user's confirmation. */
    data class ConfirmDeleteSetAside(val item: SetAsideItem, val understood: Boolean = false) :
        SecuritySettingsDialogState()

    /**
     * Shows a new recovery code once. It is dropped from state as soon as the dialog closes,
     * and `toString` never prints it.
     */
    class ShowRecoveryCode(val code: String) : SecuritySettingsDialogState() {
        override fun toString(): String = "ShowRecoveryCode([REDACTED])"
    }
}
