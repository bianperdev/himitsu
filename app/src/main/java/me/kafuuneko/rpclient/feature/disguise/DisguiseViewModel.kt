// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise

import me.kafuuneko.rpclient.feature.disguise.presentation.DisguiseUiIntent
import me.kafuuneko.rpclient.feature.disguise.presentation.DisguiseUiState
import me.kafuuneko.rpclient.feature.disguise.presentation.DisguiseViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.disguise.AfterAuthentication
import me.kafuuneko.rpclient.libs.disguise.Appearance
import me.kafuuneko.rpclient.libs.disguise.AppearanceSwitcher
import me.kafuuneko.rpclient.libs.disguise.DisguiseUnlockRouter
import me.kafuuneko.rpclient.libs.disguise.ShortcutAction
import me.kafuuneko.rpclient.libs.security.AppGate
import me.kafuuneko.rpclient.libs.security.GateState
import me.kafuuneko.rpclient.libs.security.SecureStorageManager
import me.kafuuneko.rpclient.libs.security.SecureStorageState
import me.kafuuneko.rpclient.libs.security.lock.AppLockManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * ViewModel of the frame around every decoy.
 *
 * Responsibilities:
 * - pick the decoy for the saved appearance
 * - drive the overflow menu and the About dialog
 * - turn the shortcut and the recovery gesture into authentication through
 *   [DisguiseUnlockRouter]; content opens only after a successful prompt, and a canceled or
 *   failed one leaves the decoy exactly as it was
 */
class DisguiseViewModel : CoreViewModelWithEvent<DisguiseUiIntent, DisguiseUiState>(DisguiseUiState.None), KoinComponent {
    private val mAppearanceSwitcher by inject<AppearanceSwitcher>()
    private val mStorage by inject<SecureStorageManager>()
    private val mLockManager by inject<AppLockManager>()
    private val mAppGate by inject<AppGate>()

    @UiIntentObserver(DisguiseUiIntent.Init::class)
    private fun onInit(intent: DisguiseUiIntent.Init) {
        if (!isStateOf<DisguiseUiState.None>()) return
        // This screen only opens from a decoy alias; Himitsu itself falls back to the calculator
        val appearance = mAppearanceSwitcher.current.takeIf { it.isDecoy } ?: Appearance.Calculator
        DisguiseUiState.Normal(appearance = appearance, versionName = intent.versionName, covering = intent.covering).setup()
    }

    @UiIntentObserver(DisguiseUiIntent.ShowMenu::class)
    private fun onShowMenu() {
        val uiState = getOrNull<DisguiseUiState.Normal>() ?: return
        uiState.copy(menuExpanded = true).setup()
    }

    @UiIntentObserver(DisguiseUiIntent.DismissMenu::class)
    private fun onDismissMenu() {
        val uiState = getOrNull<DisguiseUiState.Normal>() ?: return
        uiState.copy(menuExpanded = false).setup()
    }

    @UiIntentObserver(DisguiseUiIntent.ShowAbout::class)
    private fun onShowAbout() {
        val uiState = getOrNull<DisguiseUiState.Normal>() ?: return
        uiState.copy(menuExpanded = false, aboutVisible = true).setup()
    }

    @UiIntentObserver(DisguiseUiIntent.DismissAbout::class)
    private fun onDismissAbout() {
        val uiState = getOrNull<DisguiseUiState.Normal>() ?: return
        uiState.copy(aboutVisible = false).setup()
    }

    /** Closes About so nothing about the gesture stays on screen, then asks for authentication. */
    @UiIntentObserver(DisguiseUiIntent.RecoveryGesture::class)
    private fun onRecoveryGesture() {
        val uiState = getOrNull<DisguiseUiState.Normal>() ?: return
        uiState.copy(aboutVisible = false).setup()
        requestAuthentication(uiState.covering)
    }

    @UiIntentObserver(DisguiseUiIntent.ShortcutMatched::class)
    private fun onShortcutMatched() {
        val uiState = getOrNull<DisguiseUiState.Normal>() ?: return
        requestAuthentication(uiState.covering)
    }

    /** Leaves the decoy only after a successful prompt; anything else keeps it on screen, silently. */
    @UiIntentObserver(DisguiseUiIntent.AuthenticationFinished::class)
    private fun onAuthenticationFinished(intent: DisguiseUiIntent.AuthenticationFinished) {
        val uiState = getOrNull<DisguiseUiState.Normal>() ?: return
        emitAfterAuthentication(DisguiseUnlockRouter.afterAuthentication(intent.outcome, uiState.covering))
    }

    private fun requestAuthentication(covering: Boolean) {
        val action = DisguiseUnlockRouter.onShortcut(
            storageFailed = mStorage.state.value is SecureStorageState.Failed,
            deviceSecure = mLockManager.isDeviceSecure(),
            gateOpen = mAppGate.evaluateNow() == GateState.Open
        )
        when (action) {
            ShortcutAction.ShowPrompt -> DisguiseViewEvent.ShowPrompt.tryEmit()
            ShortcutAction.OpenGateScreen -> DisguiseViewEvent.OpenGateScreen.tryEmit()
            ShortcutAction.OpenContent -> emitAfterAuthentication(DisguiseUnlockRouter.contentTarget(covering))
        }
    }

    private fun emitAfterAuthentication(next: AfterAuthentication) {
        when (next) {
            AfterAuthentication.RevealPage -> DisguiseViewEvent.RevealPage.tryEmit()
            AfterAuthentication.OpenHimitsu -> DisguiseViewEvent.OpenHimitsu.tryEmit()
            AfterAuthentication.StayInDecoy -> Unit
        }
    }
}
