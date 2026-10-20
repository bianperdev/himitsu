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

    /**
     * The gate is open; the Activity leaves while still drawing [previous].
     *
     * @property restartApp true when the storage failed before opening: screens created behind
     *   the gate may hold default values read while the key was missing, so the task restarts
     */
    data class Finished(val previous: LockUiState, val restartApp: Boolean = false) : LockUiState()
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
     * @property canStartOver whether retrying cannot help, so setting the data aside is offered
     */
    data class StorageError(
        val failure: StorageFailure,
        val canRetry: Boolean,
        val canUseRecoveryCode: Boolean,
        val canStartOver: Boolean
    ) : LockContentState()

    /**
     * The app lock is on and the user must authenticate.
     *
     * @property deviceSecure false when the device has no screen lock, so authentication is
     *   impossible and the user may only continue after a warning
     * @property errorMessage system message of the last failed attempt, already localized
     */
    data class Locked(
        val deviceSecure: Boolean,
        val errorMessage: String? = null
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
