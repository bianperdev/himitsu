// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.feature.disguise.calculator.CalculatorViewModel
import me.kafuuneko.rpclient.feature.disguise.calculator.presentation.CalculatorUiIntent
import me.kafuuneko.rpclient.feature.disguise.calculator.presentation.CalculatorViewEvent
import me.kafuuneko.rpclient.feature.disguise.calculator.ui.CalculatorLayout
import me.kafuuneko.rpclient.feature.disguise.presentation.DisguiseUiIntent
import me.kafuuneko.rpclient.feature.disguise.presentation.DisguiseViewEvent
import me.kafuuneko.rpclient.feature.disguise.ui.DisguiseLayout
import me.kafuuneko.rpclient.feature.disguise.ui.DisguiseTheme
import me.kafuuneko.rpclient.libs.core.IViewEvent
import me.kafuuneko.rpclient.libs.core.ViewEventWrapper
import me.kafuuneko.rpclient.libs.security.UnlockEntry
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Screen behind the decoy launcher aliases.
 *
 * It must work while the app is locked, so it does not extend `CoreActivity` and never shows
 * user data (security contract). The shortcut and the recovery gesture only ever lead to
 * authentication.
 */
class DisguiseActivity : ComponentActivity(), KoinComponent {
    private val mViewModel by viewModels<DisguiseViewModel>()
    private val mCalculatorViewModel by viewModels<CalculatorViewModel>()
    private val mUnlockEntry by inject<UnlockEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by mViewModel.uiStateFlow.collectAsState()
            val calculatorUiState by mCalculatorViewModel.uiStateFlow.collectAsState()
            DisguiseTheme {
                DisguiseLayout(uiState = uiState, emit = { mViewModel.emit(this) }) {
                    CalculatorLayout(uiState = calculatorUiState, emit = { mCalculatorViewModel.emit(this) })
                }
            }
        }
        collectViewEvents(mViewModel.viewEventFlow)
        collectViewEvents(mCalculatorViewModel.viewEventFlow)
        mViewModel.emit(DisguiseUiIntent.Init(versionName()))
        mCalculatorViewModel.emit(CalculatorUiIntent.Init)
    }

    private fun collectViewEvents(events: Flow<ViewEventWrapper>) {
        lifecycleScope.launch {
            events.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { wrapper ->
                wrapper.consumeIfNotHandled(::onViewEvent)
            }
        }
    }

    private fun onViewEvent(event: IViewEvent) {
        when (event) {
            DisguiseViewEvent.RequestAuthentication, CalculatorViewEvent.ShortcutMatched -> openAuthentication()
        }
    }

    /**
     * Leads to the app gate. The next milestone of `feat/disguise` replaces it with the system
     * prompt shown over the decoy, which returns to the decoy when canceled.
     */
    private fun openAuthentication() {
        mUnlockEntry.open(this)
    }

    private fun versionName(): String = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
}
