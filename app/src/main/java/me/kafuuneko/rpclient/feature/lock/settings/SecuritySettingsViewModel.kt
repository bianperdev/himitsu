// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock.settings

import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.lock.settings.presentation.SecuritySettingsDialogState
import me.kafuuneko.rpclient.feature.lock.settings.presentation.SecuritySettingsUiIntent
import me.kafuuneko.rpclient.feature.lock.settings.presentation.SecuritySettingsUiState
import me.kafuuneko.rpclient.feature.lock.settings.presentation.SecuritySettingsViewEvent
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
 * - ask the user to confirm with biometrics or the device credential before turning the lock
 *   off or changing the recovery code, so an unlocked phone left on the table is not enough
 */
class SecuritySettingsViewModel :
    CoreViewModelWithEvent<SecuritySettingsUiIntent, SecuritySettingsUiState>(SecuritySettingsUiState.None),
    KoinComponent {
    private val mLockManager by inject<AppLockManager>()
    private val mStorage by inject<SecureStorageManager>()

    /** Change waiting for the confirmation; lost with the process, which then changes nothing. */
    private var mPendingAction: ProtectedAction? = null

    @UiIntentObserver(SecuritySettingsUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<SecuritySettingsUiState.None>()) return
        SecuritySettingsUiState.Normal(
            appLockEnabled = SecurityPrefs.appLockEnabled,
            deviceSecure = mLockManager.isDeviceSecure(),
            lockTimeout = mLockManager.timeout,
            hasRecoveryCode = mStorage.hasRecoveryCode(),
            secureScreenEnabled = SecurityPrefs.secureScreenEnabled
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
        if (!intent.enabled && uiState.appLockEnabled) {
            requestConfirmation(ProtectedAction.DisableLock)
            return
        }
        setAppLockEnabled(intent.enabled)
    }

    private fun setAppLockEnabled(enabled: Boolean) {
        val uiState = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        mLockManager.setEnabled(enabled)
        uiState.copy(appLockEnabled = enabled).setup()
        // The recents preview depends on the lock too
        SecuritySettingsViewEvent.ApplyWindowProtection.tryEmit()
    }

    /** Turns screenshot and recents protection on or off for every screen. */
    @UiIntentObserver(SecuritySettingsUiIntent.ToggleSecureScreen::class)
    private fun onToggleSecureScreen(intent: SecuritySettingsUiIntent.ToggleSecureScreen) {
        val uiState = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        SecurityPrefs.secureScreenEnabled = intent.enabled
        uiState.copy(secureScreenEnabled = intent.enabled).setup()
        SecuritySettingsViewEvent.ApplyWindowProtection.tryEmit()
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
        requestConfirmation(ProtectedAction.CreateRecoveryCode)
    }

    @UiIntentObserver(SecuritySettingsUiIntent.ConfirmReplaceRecoveryCode::class)
    private fun onConfirmReplaceRecoveryCode() {
        val uiState = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        if (uiState.dialogState != SecuritySettingsDialogState.ConfirmReplaceRecoveryCode) return
        uiState.copy(dialogState = SecuritySettingsDialogState.None).setup()
        requestConfirmation(ProtectedAction.CreateRecoveryCode)
    }

    @UiIntentObserver(SecuritySettingsUiIntent.ShowRemoveRecoveryCodeDialog::class)
    private fun onShowRemoveRecoveryCodeDialog() {
        val uiState = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        uiState.copy(dialogState = SecuritySettingsDialogState.ConfirmRemoveRecoveryCode).setup()
    }

    @UiIntentObserver(SecuritySettingsUiIntent.ConfirmRemoveRecoveryCode::class)
    private fun onConfirmRemoveRecoveryCode() {
        val uiState = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        if (uiState.dialogState != SecuritySettingsDialogState.ConfirmRemoveRecoveryCode || uiState.isWorking) return
        uiState.copy(dialogState = SecuritySettingsDialogState.None).setup()
        requestConfirmation(ProtectedAction.RemoveRecoveryCode)
    }

    /** Runs the pending change only when the user confirmed it. */
    @UiIntentObserver(SecuritySettingsUiIntent.ConfirmationFinished::class)
    private suspend fun onConfirmationFinished(intent: SecuritySettingsUiIntent.ConfirmationFinished) {
        val action = mPendingAction ?: return
        mPendingAction = null
        if (!intent.confirmed) {
            AppViewEvent.PopupToastMessageByResId(R.string.security_confirm_canceled).tryEmit()
            return
        }
        when (action) {
            ProtectedAction.DisableLock -> setAppLockEnabled(false)
            ProtectedAction.CreateRecoveryCode -> generateRecoveryCode()
            ProtectedAction.RemoveRecoveryCode -> removeRecoveryCode()
        }
    }

    private fun requestConfirmation(action: ProtectedAction) {
        if (mPendingAction != null) return
        mPendingAction = action
        SecuritySettingsViewEvent.RequestConfirmation.tryEmit()
    }

    /** Removes the recovery slot from the key file. */
    private suspend fun removeRecoveryCode() {
        val uiState = getOrNull<SecuritySettingsUiState.Normal>() ?: return
        if (uiState.isWorking) return
        uiState.copy(isWorking = true).setup()
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

    /** Changes that need the user's confirmation first. */
    private enum class ProtectedAction { DisableLock, CreateRecoveryCode, RemoveRecoveryCode }
}
