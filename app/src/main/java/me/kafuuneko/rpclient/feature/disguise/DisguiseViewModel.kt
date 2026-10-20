// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise

import me.kafuuneko.rpclient.feature.disguise.presentation.DisguiseUiIntent
import me.kafuuneko.rpclient.feature.disguise.presentation.DisguiseUiState
import me.kafuuneko.rpclient.feature.disguise.presentation.DisguiseViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.disguise.Appearance
import me.kafuuneko.rpclient.libs.disguise.AppearanceSwitcher
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * ViewModel of the frame around every decoy.
 *
 * Responsibilities:
 * - pick the decoy for the saved appearance
 * - drive the overflow menu and the About dialog
 * - turn the recovery gesture into an authentication request; it never opens content itself
 */
class DisguiseViewModel : CoreViewModelWithEvent<DisguiseUiIntent, DisguiseUiState>(DisguiseUiState.None), KoinComponent {
    private val mAppearanceSwitcher by inject<AppearanceSwitcher>()

    @UiIntentObserver(DisguiseUiIntent.Init::class)
    private fun onInit(intent: DisguiseUiIntent.Init) {
        if (!isStateOf<DisguiseUiState.None>()) return
        // This screen only opens from a decoy alias; Himitsu itself falls back to the calculator
        val appearance = mAppearanceSwitcher.current.takeIf { it.isDecoy } ?: Appearance.Calculator
        DisguiseUiState.Normal(appearance = appearance, versionName = intent.versionName).setup()
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
        DisguiseViewEvent.RequestAuthentication.tryEmit()
    }
}
