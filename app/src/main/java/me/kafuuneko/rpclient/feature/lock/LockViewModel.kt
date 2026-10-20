// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock

import android.content.Context
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.lock.presentation.LockContentState
import me.kafuuneko.rpclient.feature.lock.presentation.LockDialogState
import me.kafuuneko.rpclient.feature.lock.presentation.LockUiIntent
import me.kafuuneko.rpclient.feature.lock.presentation.LockUiState
import me.kafuuneko.rpclient.feature.lock.presentation.LockViewEvent
import me.kafuuneko.rpclient.feature.lock.presentation.RecoveryCodeError
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.security.AppGate
import me.kafuuneko.rpclient.libs.security.GateState
import me.kafuuneko.rpclient.libs.security.SecureStorageManager
import me.kafuuneko.rpclient.libs.security.SecureStorageState
import me.kafuuneko.rpclient.libs.security.StorageFailure
import me.kafuuneko.rpclient.libs.security.key.RecoveryCode
import me.kafuuneko.rpclient.libs.security.key.RecoveryResult
import me.kafuuneko.rpclient.libs.security.lock.AppLockManager
import me.kafuuneko.rpclient.libs.security.lock.AuthOutcome
import me.kafuuneko.rpclient.libs.security.storage.EncryptedExport
import me.kafuuneko.rpclient.libs.security.storage.ProtectedFiles
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * ViewModel of the gate screen, shown whenever the app gate is not open.
 *
 * Responsibilities:
 * - mirror the encrypted storage state, and offer retry, recovery code, start over, setting a
 *   damaged plain database aside and saving a copy of the files when it cannot be opened;
 *   none of these deletes data
 * - when the app is locked, ask the host to show the system prompt, automatically on arrival
 *   and on return to the screen; unlocking itself happens inside [AppLockManager]
 * - finish as soon as the gate opens
 */
class LockViewModel : CoreViewModelWithEvent<LockUiIntent, LockUiState>(LockUiState.None), KoinComponent {
    private val mStorage by inject<SecureStorageManager>()
    private val mLockManager by inject<AppLockManager>()
    private val mAppGate by inject<AppGate>()
    private val mProtectedFiles by inject<ProtectedFiles>()
    private val mContext by inject<Context>()

    /** Whether the storage failed while this screen was shown (privacy review M3). */
    private var mStorageFailed = false

    /**
     * [AppLockManager.backgroundCount] when the user dismissed the prompt, or null. While it
     * matches, returning to this screen does not reopen the prompt; only the button does.
     */
    private var mDeclinedAtBackgroundCount: Int? = null

    @UiIntentObserver(LockUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<LockUiState.None>()) return
        LockUiState.Normal(LockContentState.Loading).setup()
        viewModelScope.launch {
            combine(mStorage.state, mLockManager.isLocked) { storage, locked -> storage to locked }
                .collect { (storage, locked) -> onGateInputs(storage, locked) }
        }
        viewModelScope.launch {
            mAppGate.state.first { it == GateState.Open }
            val current = uiStateFlow.value
            if (current !is LockUiState.Finished) LockUiState.Finished(current, restartApp = mStorageFailed).setup()
        }
    }

    /** Shows the prompt again when the user comes back to a locked screen. */
    @UiIntentObserver(LockUiIntent.Resume::class)
    private fun onResume() {
        val uiState = getOrNull<LockUiState.Normal>() ?: return
        val content = uiState.contentState as? LockContentState.Locked ?: return
        // Closing the prompt also resumes this screen; reopening it then would trap the user
        if (mDeclinedAtBackgroundCount == mLockManager.backgroundCount) return
        mDeclinedAtBackgroundCount = null
        if (content.deviceSecure) requestPrompt()
    }

    /** Shows the prompt after the user tapped the unlock button. */
    @UiIntentObserver(LockUiIntent.Unlock::class)
    private fun onUnlock() {
        val uiState = getOrNull<LockUiState.Normal>() ?: return
        val content = uiState.contentState as? LockContentState.Locked ?: return
        mDeclinedAtBackgroundCount = null
        if (content.deviceSecure) requestPrompt()
    }

    /**
     * Handles the prompt result. Success needs nothing here: the lock manager already unlocked
     * and the gate closes this screen. Other outcomes keep the screen with an unlock button.
     */
    @UiIntentObserver(LockUiIntent.AuthenticationFinished::class)
    private fun onAuthenticationFinished(intent: LockUiIntent.AuthenticationFinished) {
        val uiState = getOrNull<LockUiState.Normal>() ?: return
        val content = uiState.contentState as? LockContentState.Locked ?: return
        if (intent.outcome != AuthOutcome.Succeeded) mDeclinedAtBackgroundCount = mLockManager.backgroundCount
        val message = (intent.outcome as? AuthOutcome.Error)?.message
        uiState.copy(contentState = content.copy(errorMessage = message)).setup()
    }

    /** Lets the user in when the device has no screen lock, after the warning on screen. */
    @UiIntentObserver(LockUiIntent.ContinueWithoutDeviceLock::class)
    private fun onContinueWithoutDeviceLock() {
        val uiState = getOrNull<LockUiState.Normal>() ?: return
        val content = uiState.contentState as? LockContentState.Locked ?: return
        if (content.deviceSecure) return
        if (!mLockManager.continueWithoutDeviceLock()) {
            // The user set a screen lock meanwhile: authenticate normally
            uiState.copy(contentState = content.copy(deviceSecure = true)).setup()
            requestPrompt()
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
        if ((uiState.contentState as? LockContentState.StorageError)?.canStartOver != true) return
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

    /** Asks for confirmation before setting a damaged plain database aside. */
    @UiIntentObserver(LockUiIntent.ShowSetAsideDialog::class)
    private fun onShowSetAsideDialog() {
        val uiState = getOrNull<LockUiState.Normal>() ?: return
        if ((uiState.contentState as? LockContentState.StorageError)?.canSetAside != true) return
        uiState.copy(dialogState = LockDialogState.ConfirmSetAside).setup()
    }

    /** Sets the damaged plain database aside, without deleting it, and continues without it. */
    @UiIntentObserver(LockUiIntent.ConfirmSetAside::class)
    private fun onConfirmSetAside() {
        val uiState = getOrNull<LockUiState.Normal>() ?: return
        if (uiState.dialogState != LockDialogState.ConfirmSetAside) return
        uiState.copy(dialogState = LockDialogState.None).setup()
        mStorage.setAsideUnusableDatabase()
    }

    /** Explains what an export holds before the file picker opens. */
    @UiIntentObserver(LockUiIntent.ShowExportDialog::class)
    private fun onShowExportDialog() {
        val uiState = getOrNull<LockUiState.Normal>() ?: return
        val content = uiState.contentState as? LockContentState.StorageError ?: return
        if (content.isExporting) return
        uiState.copy(dialogState = LockDialogState.ConfirmExport).setup()
    }

    @UiIntentObserver(LockUiIntent.ConfirmExport::class)
    private fun onConfirmExport() {
        val uiState = getOrNull<LockUiState.Normal>() ?: return
        if (uiState.dialogState != LockDialogState.ConfirmExport) return
        uiState.copy(dialogState = LockDialogState.None).setup()
        val date = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        LockViewEvent.PickExportDestination("himitsu-encrypted-$date.zip").tryEmit()
    }

    /**
     * Saves the files exactly as they are on disk into the chosen document. It only reads them,
     * so a failure halfway leaves the data untouched.
     */
    @UiIntentObserver(LockUiIntent.ExportTo::class)
    private suspend fun onExportTo(intent: LockUiIntent.ExportTo) {
        val destination = intent.destination ?: return
        val uiState = getOrNull<LockUiState.Normal>() ?: return
        val content = uiState.contentState as? LockContentState.StorageError ?: return
        if (content.isExporting) return
        uiState.copy(contentState = content.copy(isExporting = true)).setup()
        val saved = withContext(Dispatchers.IO) {
            val context = currentCoroutineContext()
            runCatching {
                val output = mContext.contentResolver.openOutputStream(destination) ?: error("No output")
                output.use { EncryptedExport.write(mProtectedFiles.current(), it) { context.ensureActive() } }
            }.isSuccess
        }
        LockViewEvent.ShowMessage(if (saved) R.string.security_export_done else R.string.security_export_failed).tryEmit()
        val current = getOrNull<LockUiState.Normal>() ?: return
        val currentContent = current.contentState as? LockContentState.StorageError ?: return
        current.copy(contentState = currentContent.copy(isExporting = false)).setup()
    }

    @UiIntentObserver(LockUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<LockUiState.Normal>() ?: return
        val dialog = uiState.dialogState
        if (dialog is LockDialogState.RecoveryCodeInput && dialog.isWorking) return
        uiState.copy(dialogState = LockDialogState.None).setup()
    }

    /**
     * Maps storage and lock state to the screen content.
     *
     * - Storage problems come first: there is nothing to unlock until the data can be opened.
     * - Entering the locked state shows the prompt at once when the device can authenticate.
     */
    private fun onGateInputs(storage: SecureStorageState, locked: Boolean) {
        val uiState = getOrNull<LockUiState.Normal>() ?: return
        val previous = uiState.contentState
        if (storage is SecureStorageState.Failed) mStorageFailed = true
        val content = when (storage) {
            SecureStorageState.Starting -> LockContentState.Loading
            is SecureStorageState.Failed -> LockContentState.StorageError(
                failure = storage.failure,
                canRetry = storage.failure != StorageFailure.KeystoreKeyLost,
                canUseRecoveryCode = storage.recoveryAvailable,
                canStartOver = storage.failure in StorageFailure.START_OVER_ALLOWED,
                canSetAside = storage.failure == StorageFailure.PlainDatabaseUnusable,
                isExporting = (previous as? LockContentState.StorageError)?.isExporting == true
            )
            SecureStorageState.Ready -> if (locked) {
                previous as? LockContentState.Locked ?: LockContentState.Locked(mLockManager.isDeviceSecure())
            } else {
                LockContentState.Loading
            }
        }
        // A recovery attempt in progress keeps its dialog; other dialogs close on state changes
        val dialog = uiState.dialogState
        val keepDialog = dialog is LockDialogState.RecoveryCodeInput && content is LockContentState.StorageError
        uiState.copy(
            contentState = content,
            dialogState = if (keepDialog) dialog else LockDialogState.None
        ).setup()
        if (content is LockContentState.Locked && previous !is LockContentState.Locked && content.deviceSecure) {
            requestPrompt()
        }
    }

    /**
     * Asks the host for the prompt. The host ignores the request while a prompt is already on
     * screen, and an event dropped before the host listens is repeated by [onResume].
     */
    private fun requestPrompt() {
        LockViewEvent.ShowAuthenticationPrompt.tryEmit()
    }
}
