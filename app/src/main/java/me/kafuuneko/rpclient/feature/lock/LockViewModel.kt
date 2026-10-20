// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.feature.lock.presentation.LockContentState
import me.kafuuneko.rpclient.feature.lock.presentation.LockDialogState
import me.kafuuneko.rpclient.feature.lock.presentation.LockUiIntent
import me.kafuuneko.rpclient.feature.lock.presentation.LockUiState
import me.kafuuneko.rpclient.feature.lock.presentation.RecoveryCodeError
import me.kafuuneko.rpclient.libs.core.CoreViewModel
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.security.AppGate
import me.kafuuneko.rpclient.libs.security.GateState
import me.kafuuneko.rpclient.libs.security.SecureStorageManager
import me.kafuuneko.rpclient.libs.security.SecureStorageState
import me.kafuuneko.rpclient.libs.security.StorageFailure
import me.kafuuneko.rpclient.libs.security.key.RecoveryCode
import me.kafuuneko.rpclient.libs.security.key.RecoveryResult
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * ViewModel of the gate screen, shown whenever the app gate is not open.
 *
 * Responsibilities:
 * - mirror the encrypted storage state, and offer retry, recovery code and start over when it
 *   cannot be opened; none of these deletes data
 * - finish as soon as the gate opens
 */
class LockViewModel : CoreViewModel<LockUiIntent, LockUiState>(LockUiState.None), KoinComponent {
    private val mStorage by inject<SecureStorageManager>()
    private val mAppGate by inject<AppGate>()

    @UiIntentObserver(LockUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<LockUiState.None>()) return
        LockUiState.Normal(LockContentState.Loading).setup()
        viewModelScope.launch { mStorage.state.collect(::onStorageState) }
        viewModelScope.launch {
            mAppGate.state.first { it == GateState.Open }
            if (nowState !is LockUiState.Finished) LockUiState.Finished(nowState).setup()
        }
    }

    /** Retries opening the storage after a failure that may be temporary. */
    @UiIntentObserver(LockUiIntent.Retry::class)
    private fun onRetry() {
        getOrNull<LockUiState.Normal>() ?: return
        mStorage.retry()
    }

    /** Opens the recovery code dialog. */
    @UiIntentObserver(LockUiIntent.ShowRecoveryCodeDialog::class)
    private fun onShowRecoveryCodeDialog() {
        val uiState = getOrNull<LockUiState.Normal>() ?: return
        uiState.copy(dialogState = LockDialogState.RecoveryCodeInput()).setup()
    }

    /**
     * Unlocks the storage with a recovery code.
     *
     * - The typed text is parsed, used once and wiped; it is never stored in UI state.
     * - On success the storage resumes its startup and the gate opens by itself.
     */
    @UiIntentObserver(LockUiIntent.SubmitRecoveryCode::class)
    private suspend fun onSubmitRecoveryCode(intent: LockUiIntent.SubmitRecoveryCode) {
        val uiState = getOrNull<LockUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? LockDialogState.RecoveryCodeInput ?: return
        if (dialog.isWorking) return
        // Reject malformed input before touching the vault
        val code = RecoveryCode.parse(intent.code)
        if (code == null) {
            uiState.copy(dialogState = dialog.copy(error = RecoveryCodeError.Malformed)).setup()
            return
        }
        uiState.copy(dialogState = LockDialogState.RecoveryCodeInput(isWorking = true)).setup()
        val result = try {
            mStorage.recover(code)
        } finally {
            code.wipe()
        }
        // Report the result in the dialog; success closes it through the storage state
        val error = when (result) {
            is RecoveryResult.Recovered -> null
            RecoveryResult.WrongCode -> RecoveryCodeError.WrongCode
            RecoveryResult.NotConfigured -> RecoveryCodeError.NotConfigured
            is RecoveryResult.Failed -> RecoveryCodeError.Failed
        }
        val current = getOrNull<LockUiState.Normal>() ?: return
        val dialogState = if (error == null) LockDialogState.None else LockDialogState.RecoveryCodeInput(error = error)
        current.copy(dialogState = dialogState).setup()
    }

    /** Asks for confirmation before setting the current data aside. */
    @UiIntentObserver(LockUiIntent.ShowStartOverDialog::class)
    private fun onShowStartOverDialog() {
        val uiState = getOrNull<LockUiState.Normal>() ?: return
        uiState.copy(dialogState = LockDialogState.ConfirmStartOver).setup()
    }

    /** Sets the current data aside, without deleting it, and starts with empty storage. */
    @UiIntentObserver(LockUiIntent.ConfirmStartOver::class)
    private fun onConfirmStartOver() {
        val uiState = getOrNull<LockUiState.Normal>() ?: return
        if (uiState.dialogState != LockDialogState.ConfirmStartOver) return
        uiState.copy(dialogState = LockDialogState.None).setup()
        mStorage.startOver()
    }

    @UiIntentObserver(LockUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<LockUiState.Normal>() ?: return
        val dialog = uiState.dialogState
        if (dialog is LockDialogState.RecoveryCodeInput && dialog.isWorking) return
        uiState.copy(dialogState = LockDialogState.None).setup()
    }

    private fun onStorageState(state: SecureStorageState) {
        val uiState = getOrNull<LockUiState.Normal>() ?: return
        val content = when (state) {
            SecureStorageState.Starting, SecureStorageState.Ready -> LockContentState.Loading
            is SecureStorageState.Failed -> LockContentState.StorageError(
                failure = state.failure,
                canRetry = state.failure != StorageFailure.KeystoreKeyLost,
                canUseRecoveryCode = state.recoveryAvailable
            )
        }
        // A recovery attempt in progress keeps its dialog; other dialogs close on state changes
        val dialog = uiState.dialogState
        val keepDialog = dialog is LockDialogState.RecoveryCodeInput && content is LockContentState.StorageError
        uiState.copy(
            contentState = content,
            dialogState = if (keepDialog) dialog else LockDialogState.None
        ).setup()
    }

    private val nowState: LockUiState
        get() = uiStateFlow.value
}
