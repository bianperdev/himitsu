// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock.settings

import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.lock.settings.presentation.SecuritySettingsDialogState
import me.kafuuneko.rpclient.feature.lock.settings.presentation.SecuritySettingsUiIntent
import me.kafuuneko.rpclient.feature.lock.settings.presentation.SecuritySettingsUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.security.SecureStorageManager
import me.kafuuneko.rpclient.libs.security.lock.AppLockManager
import me.kafuuneko.rpclient.libs.security.lock.SecurityPrefs
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * ViewModel of the security settings screen.
 *
 * Responsibilities:
 * - turn the app lock on or off and choose how long the app may stay in the background
 * - create, replace and remove the recovery code; a new code is shown once and then dropped
 *   from state
 */
class SecuritySettingsViewModel :
    CoreViewModelWithEvent<SecuritySettingsUiIntent, SecuritySettingsUiState>(SecuritySettingsUiState.None),
    KoinComponent {
    private val mLockManager by inject<AppLockManager>()
    private val mStorage by inject<SecureStorageManager>()

    @UiIntentObserver(SecuritySettingsUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<SecuritySettingsUiState.None>()) return
        SecuritySettingsUiState.Normal(
            appLockEnabled = SecurityPrefs.appLockEnabled,
            deviceSecure = mLockManager.isDeviceSecure(),
            lockTimeout = mLockManager.timeout,
            hasRecoveryCode = mStorage.hasRecoveryCode()
        ).setup()
    }

    /** Refreshes the device lock status, which the user may change in the system settings. */
    @UiIntentObserver(SecuritySettingsUiIntent.Resume::class)
    private fun onResume() {
        val uiState = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        uiState.copy(deviceSecure = mLockManager.isDeviceSecure()).setup()
    }

    @UiIntentObserver(SecuritySettingsUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<SecuritySettingsUiState.Finished>()) return
        SecuritySettingsUiState.Finished(uiStateFlow.value).setup()
    }

    /** Turns the app lock on or off; turning it on needs a device screen lock. */
    @UiIntentObserver(SecuritySettingsUiIntent.ToggleAppLock::class)
    private fun onToggleAppLock(intent: SecuritySettingsUiIntent.ToggleAppLock) {
        val uiState = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        if (intent.enabled && !mLockManager.isDeviceSecure()) {
            AppViewEvent.PopupToastMessageByResId(R.string.security_settings_lock_needs_device_lock).tryEmit()
            uiState.copy(deviceSecure = false).setup()
            return
        }
        mLockManager.setEnabled(intent.enabled)
        uiState.copy(appLockEnabled = intent.enabled).setup()
    }

    @UiIntentObserver(SecuritySettingsUiIntent.ShowTimeoutPicker::class)
    private fun onShowTimeoutPicker() {
        val uiState = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        uiState.copy(dialogState = SecuritySettingsDialogState.TimeoutPicker).setup()
    }

    @UiIntentObserver(SecuritySettingsUiIntent.SelectTimeout::class)
    private fun onSelectTimeout(intent: SecuritySettingsUiIntent.SelectTimeout) {
        val uiState = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        mLockManager.timeout = intent.timeout
        uiState.copy(lockTimeout = intent.timeout, dialogState = SecuritySettingsDialogState.None).setup()
    }

    /** Creates a recovery code, asking first when one already exists. */
    @UiIntentObserver(SecuritySettingsUiIntent.CreateRecoveryCode::class)
    private suspend fun onCreateRecoveryCode() {
        val uiState = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        if (uiState.hasRecoveryCode) {
            uiState.copy(dialogState = SecuritySettingsDialogState.ConfirmReplaceRecoveryCode).setup()
            return
        }
        generateRecoveryCode()
    }

    @UiIntentObserver(SecuritySettingsUiIntent.ConfirmReplaceRecoveryCode::class)
    private suspend fun onConfirmReplaceRecoveryCode() {
        val uiState = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        if (uiState.dialogState != SecuritySettingsDialogState.ConfirmReplaceRecoveryCode) return
        generateRecoveryCode()
    }

    @UiIntentObserver(SecuritySettingsUiIntent.ShowRemoveRecoveryCodeDialog::class)
    private fun onShowRemoveRecoveryCodeDialog() {
        val uiState = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        uiState.copy(dialogState = SecuritySettingsDialogState.ConfirmRemoveRecoveryCode).setup()
    }

    /** Removes the recovery slot from the key file. */
    @UiIntentObserver(SecuritySettingsUiIntent.ConfirmRemoveRecoveryCode::class)
    private suspend fun onConfirmRemoveRecoveryCode() {
        val uiState = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        if (uiState.dialogState != SecuritySettingsDialogState.ConfirmRemoveRecoveryCode || uiState.isWorking) return
        uiState.copy(isWorking = true, dialogState = SecuritySettingsDialogState.None).setup()
        val removed = mStorage.removeRecoveryCode()
        if (!removed) AppViewEvent.PopupToastMessageByResId(R.string.security_recovery_update_failed).tryEmit()
        val current = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        current.copy(isWorking = false, hasRecoveryCode = mStorage.hasRecoveryCode()).setup()
    }

    /** Closes any dialog; closing the code dialog drops the code from state. */
    @UiIntentObserver(SecuritySettingsUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        uiState.copy(dialogState = SecuritySettingsDialogState.None).setup()
    }

    /**
     * Generates and stores a new recovery code, then shows it once.
     *
     * The code object is wiped right after its display text is taken.
     */
    private suspend fun generateRecoveryCode() {
        val uiState = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        if (uiState.isWorking) return
        uiState.copy(isWorking = true, dialogState = SecuritySettingsDialogState.None).setup()
        val code = mStorage.createRecoveryCode()
        val current = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        if (code == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.security_recovery_update_failed).tryEmit()
            current.copy(isWorking = false).setup()
            return
        }
        val display = code.toDisplayString()
        code.wipe()
        current.copy(
            isWorking = false,
            hasRecoveryCode = true,
            dialogState = SecuritySettingsDialogState.ShowRecoveryCode(display)
        ).setup()
    }
}
