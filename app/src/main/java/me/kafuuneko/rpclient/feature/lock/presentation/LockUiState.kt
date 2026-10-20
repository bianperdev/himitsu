// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock.presentation

import me.kafuuneko.rpclient.libs.security.StorageFailure

/** State of the gate screen. */
sealed class LockUiState {
    data object None : LockUiState()

    data class Normal(
        val contentState: LockContentState,
        val dialogState: LockDialogState = LockDialogState.None
    ) : LockUiState()

    /** The gate is open; the Activity leaves while still drawing [previous]. */
    data class Finished(val previous: LockUiState) : LockUiState()
}

/** Main content of the gate screen. */
sealed class LockContentState {
    /** The encrypted storage is starting. */
    data object Loading : LockContentState()

    /**
     * The encrypted storage cannot be opened.
     *
     * @property canRetry whether the failure may be temporary
     * @property canUseRecoveryCode whether a recovery code is configured for this data
     */
    data class StorageError(
        val failure: StorageFailure,
        val canRetry: Boolean,
        val canUseRecoveryCode: Boolean
    ) : LockContentState()
}

/** Dialogs of the gate screen. The recovery code itself is never kept in state. */
sealed class LockDialogState {
    data object None : LockDialogState()

    data class RecoveryCodeInput(
        val isWorking: Boolean = false,
        val error: RecoveryCodeError? = null
    ) : LockDialogState()

    data object ConfirmStartOver : LockDialogState()
}

/** Why a recovery code was not accepted. */
enum class RecoveryCodeError {
    /** Wrong length or symbols. */
    Malformed,

    /** Well-formed but does not unlock the data. */
    WrongCode,

    /** The data has no recovery code. */
    NotConfigured,

    /** The data was unlocked but could not be sealed again. */
    Failed
}
