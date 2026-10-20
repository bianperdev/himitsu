// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.calculator

import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.feature.disguise.calculator.presentation.CalculatorUiIntent
import me.kafuuneko.rpclient.feature.disguise.calculator.presentation.CalculatorUiState
import me.kafuuneko.rpclient.feature.disguise.calculator.presentation.CalculatorViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.disguise.Appearance
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorEngine
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorFormat
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorKey
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorState
import me.kafuuneko.rpclient.libs.disguise.secret.DisguiseSecretStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Lengths a calculator disguise code may have. */
val CALCULATOR_CODE_LENGTHS: IntRange = 4..10

/**
 * ViewModel of the calculator decoy.
 *
 * Responsibilities:
 * - apply key presses through [CalculatorEngine] and format the display for the user's locale
 * - when "=" is pressed on a typed number shaped like the code, check it in the background;
 *   the display always behaves like a normal calculator, whatever the outcome
 * - on a match, ask the host for authentication; it never opens content itself
 */
class CalculatorViewModel : CoreViewModelWithEvent<CalculatorUiIntent, CalculatorUiState>(CalculatorUiState.None), KoinComponent {
    private val mSecretStore by inject<DisguiseSecretStore>()
    private val mFormat = CalculatorFormat(Locale.getDefault())

    @UiIntentObserver(CalculatorUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<CalculatorUiState.None>()) return
        render(CalculatorState())
    }

    @UiIntentObserver(CalculatorUiIntent.Press::class)
    private fun onPress(intent: CalculatorUiIntent.Press) {
        val uiState = getOrNull<CalculatorUiState.Normal>() ?: return
        // The candidate is read before "=" replaces the display with its result
        val candidate = if (intent.key == CalculatorKey.Equals) {
            CalculatorEngine.shortcutCandidate(uiState.engine, CALCULATOR_CODE_LENGTHS)
        } else {
            null
        }
        render(CalculatorEngine.press(uiState.engine, intent.key))
        if (candidate != null) checkShortcut(candidate)
    }

    private fun checkShortcut(candidate: String) {
        viewModelScope.launch {
            if (mSecretStore.verify(Appearance.Calculator, candidate.toCharArray())) {
                // The code must not stay on the display, which shows again when leaving Himitsu
                render(CalculatorState())
                CalculatorViewEvent.ShortcutMatched.tryEmit()
            }
        }
    }

    private fun render(engine: CalculatorState) {
        calculatorUiState(engine, mFormat).setup()
    }
}

/** The calculator display for [engine], formatted with [format]. Shared with the practice step of the settings. */
fun calculatorUiState(engine: CalculatorState, format: CalculatorFormat): CalculatorUiState.Normal =
    CalculatorUiState.Normal(
        engine = engine,
        expression = format.expression(engine.tokens),
        preview = CalculatorEngine.preview(engine)?.let(format::result),
        error = engine.error,
        decimalSeparator = format.decimalSeparator
    )
