// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.disguise.calculator.CalculatorViewModel
import me.kafuuneko.rpclient.feature.disguise.calculator.presentation.CalculatorUiIntent
import me.kafuuneko.rpclient.feature.disguise.calculator.presentation.CalculatorViewEvent
import me.kafuuneko.rpclient.feature.disguise.calculator.ui.CalculatorLayout
import me.kafuuneko.rpclient.feature.disguise.compass.CompassViewModel
import me.kafuuneko.rpclient.feature.disguise.compass.presentation.CompassUiIntent
import me.kafuuneko.rpclient.feature.disguise.compass.presentation.CompassViewEvent
import me.kafuuneko.rpclient.feature.disguise.compass.ui.CompassLayout
import me.kafuuneko.rpclient.feature.disguise.converter.ConverterViewModel
import me.kafuuneko.rpclient.feature.disguise.converter.presentation.ConverterUiIntent
import me.kafuuneko.rpclient.feature.disguise.converter.presentation.ConverterViewEvent
import me.kafuuneko.rpclient.feature.disguise.converter.ui.ConverterLayout
import me.kafuuneko.rpclient.feature.disguise.presentation.DisguiseUiIntent
import me.kafuuneko.rpclient.feature.disguise.presentation.DisguiseViewEvent
import me.kafuuneko.rpclient.feature.disguise.ui.DisguiseLayout
import me.kafuuneko.rpclient.feature.disguise.ui.DisguiseTheme
import me.kafuuneko.rpclient.feature.lock.LockActivityEntry
import me.kafuuneko.rpclient.feature.main.MainActivity
import me.kafuuneko.rpclient.libs.core.IViewEvent
import me.kafuuneko.rpclient.libs.core.ViewEventWrapper
import me.kafuuneko.rpclient.libs.disguise.Appearance
import me.kafuuneko.rpclient.libs.disguise.AppearanceSwitcher
import me.kafuuneko.rpclient.libs.disguise.DisguiseTaskDescription
import me.kafuuneko.rpclient.libs.security.lock.AppLockManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Screen of the decoys, opened from a decoy launcher alias or over a Himitsu page that locked.
 *
 * - It must work while the app is locked, so it does not extend `CoreActivity` and never shows
 *   user data (security contract).
 * - It shows the decoy of the saved appearance; only that decoy's ViewModel is created.
 * - The shortcut and the recovery gesture show the system prompt through [AppLockManager]; the
 *   app unlocks only in its success callback. Canceling or failing leaves the decoy as it was.
 * - In covering mode, Back leaves the app instead of revealing the page underneath.
 */
class DisguiseActivity : FragmentActivity(), KoinComponent {
    private val mViewModel by viewModels<DisguiseViewModel>()
    private val mCalculatorViewModel by viewModels<CalculatorViewModel>()
    private val mCompassViewModel by viewModels<CompassViewModel>()
    private val mConverterViewModel by viewModels<ConverterViewModel>()
    private val mLockManager by inject<AppLockManager>()
    private val mAppearanceSwitcher by inject<AppearanceSwitcher>()
    private lateinit var mPrompt: BiometricPrompt

    /** The decoy on screen. This screen only opens from a decoy; Himitsu itself falls back to the calculator. */
    private val mAppearance: Appearance by lazy {
        mAppearanceSwitcher.current.takeIf { it.isDecoy } ?: Appearance.Calculator
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DisguiseTaskDescription.apply(this)
        val covering = intent.getBooleanExtra(EXTRA_COVERING, false)
        if (covering) {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    moveTaskToBack(true)
                }
            })
        }
        // BiometricPrompt must be created in onCreate to survive configuration changes
        mPrompt = mLockManager.createPrompt(this) { outcome ->
            mViewModel.emit(DisguiseUiIntent.AuthenticationFinished(outcome))
        }
        setContent {
            val uiState by mViewModel.uiStateFlow.collectAsState()
            DisguiseTheme {
                DisguiseLayout(uiState = uiState, emit = { mViewModel.emit(this) }) { DecoyContent() }
            }
        }
        collectViewEvents(mViewModel.viewEventFlow)
        initDecoy()
        mViewModel.emit(DisguiseUiIntent.Init(mAppearance, versionName(), covering))
    }

    override fun onResume() {
        super.onResume()
        if (mAppearance == Appearance.Compass) {
            val rotation = ContextCompat.getDisplayOrDefault(this).rotation
            mCompassViewModel.emit(CompassUiIntent.Resume(rotation))
        }
    }

    override fun onPause() {
        super.onPause()
        if (mAppearance == Appearance.Compass) mCompassViewModel.emit(CompassUiIntent.Pause)
    }

    /** Starts the ViewModel of the decoy on screen and listens for its shortcut. */
    private fun initDecoy() {
        when (mAppearance) {
            Appearance.Compass -> {
                collectViewEvents(mCompassViewModel.viewEventFlow)
                mCompassViewModel.emit(CompassUiIntent.Init)
            }
            Appearance.Converter -> {
                collectViewEvents(mConverterViewModel.viewEventFlow)
                mConverterViewModel.emit(ConverterUiIntent.Init)
            }
            Appearance.Calculator, Appearance.Himitsu -> {
                collectViewEvents(mCalculatorViewModel.viewEventFlow)
                mCalculatorViewModel.emit(CalculatorUiIntent.Init)
            }
        }
    }

    @Composable
    private fun DecoyContent() {
        when (mAppearance) {
            Appearance.Compass -> {
                val state by mCompassViewModel.uiStateFlow.collectAsState()
                CompassLayout(uiState = state, emit = { mCompassViewModel.emit(this) })
            }
            Appearance.Converter -> {
                val state by mConverterViewModel.uiStateFlow.collectAsState()
                ConverterLayout(uiState = state, emit = { mConverterViewModel.emit(this) })
            }
            Appearance.Calculator, Appearance.Himitsu -> {
                val state by mCalculatorViewModel.uiStateFlow.collectAsState()
                CalculatorLayout(uiState = state, emit = { mCalculatorViewModel.emit(this) })
            }
        }
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
            CalculatorViewEvent.ShortcutMatched, CompassViewEvent.ShortcutMatched, ConverterViewEvent.ShortcutMatched ->
                mViewModel.emit(DisguiseUiIntent.ShortcutMatched)
            DisguiseViewEvent.ShowPrompt -> showPrompt()
            // The gate screen is opened directly: UnlockEntry would bring this decoy back
            DisguiseViewEvent.OpenGateScreen -> LockActivityEntry.open(this)
            DisguiseViewEvent.RevealPage -> finish()
            DisguiseViewEvent.OpenHimitsu -> startActivity(Intent(this, MainActivity::class.java))
        }
    }

    /** Neutral texts: the prompt is shown over the decoy and must not name Himitsu. */
    private fun showPrompt() {
        if (mLockManager.isAuthenticating) return
        mLockManager.authenticate(
            prompt = mPrompt,
            title = getString(R.string.disguise_auth_title),
            subtitle = getString(R.string.disguise_auth_subtitle)
        )
    }

    private fun versionName(): String = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

    companion object {
        /** Boolean extra: the decoy covers a Himitsu page because the app locked. */
        const val EXTRA_COVERING = "me.kafuuneko.rpclient.disguise.COVERING"
    }
}
