// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.converter

import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.feature.disguise.calculator.CALCULATOR_CODE_LENGTHS
import me.kafuuneko.rpclient.feature.disguise.converter.presentation.ConverterUiIntent
import me.kafuuneko.rpclient.feature.disguise.converter.presentation.ConverterUiState
import me.kafuuneko.rpclient.feature.disguise.converter.presentation.ConverterViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.disguise.Appearance
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorFormat
import me.kafuuneko.rpclient.libs.disguise.converter.ConverterEngine
import me.kafuuneko.rpclient.libs.disguise.converter.ConverterState
import me.kafuuneko.rpclient.libs.disguise.secret.DisguiseSecretStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * ViewModel of the unit converter decoy.
 *
 * Responsibilities:
 * - convert as the user types, with the keypad of the screen (the system keyboard would learn
 *   and suggest the code)
 * - when the swap button is pressed on a value shaped like the code, check the value and the
 *   two units in the background; the swap happens either way
 * - on a match, ask the host for authentication; it never opens content itself
 */
class ConverterViewModel : CoreViewModelWithEvent<ConverterUiIntent, ConverterUiState>(ConverterUiState.None), KoinComponent {
    private val mSecretStore by inject<DisguiseSecretStore>()
    private val mFormat = CalculatorFormat(Locale.getDefault())

    @UiIntentObserver(ConverterUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<ConverterUiState.None>()) return
        converterUiState(ConverterState(), mFormat).setup()
    }

    @UiIntentObserver(ConverterUiIntent.Press::class)
    private fun onPress(intent: ConverterUiIntent.Press) = update { ConverterEngine.press(it, intent.key) }

    @UiIntentObserver(ConverterUiIntent.SelectCategory::class)
    private fun onSelectCategory(intent: ConverterUiIntent.SelectCategory) = update { ConverterEngine.selectCategory(it, intent.category) }

    @UiIntentObserver(ConverterUiIntent.SelectFrom::class)
    private fun onSelectFrom(intent: ConverterUiIntent.SelectFrom) = update { ConverterEngine.selectFrom(it, intent.unit) }

    @UiIntentObserver(ConverterUiIntent.SelectTo::class)
    private fun onSelectTo(intent: ConverterUiIntent.SelectTo) = update { ConverterEngine.selectTo(it, intent.unit) }

    /** Swaps the units; the candidate is read before the swap, with the units the user picked. */
    @UiIntentObserver(ConverterUiIntent.Swap::class)
    private fun onSwap() {
        val uiState = getOrNull<ConverterUiState.Normal>() ?: return
        val candidate = ConverterEngine.shortcutCandidate(uiState.engine, CALCULATOR_CODE_LENGTHS)
        converterUiState(ConverterEngine.swap(uiState.engine), mFormat).setup()
        if (candidate == null) return
        viewModelScope.launch {
            if (mSecretStore.verify(Appearance.Converter, candidate.toCharArray())) {
                // The code must not stay on the display, which shows again when leaving Himitsu
                update { it.copy(input = "") }
                ConverterViewEvent.ShortcutMatched.tryEmit()
            }
        }
    }

    private fun update(change: (ConverterState) -> ConverterState) {
        val uiState = getOrNull<ConverterUiState.Normal>() ?: return
        converterUiState(change(uiState.engine), mFormat).setup()
    }
}

/** The converter display for [engine]. Shared with the practice step of the settings. */
fun converterUiState(engine: ConverterState, format: CalculatorFormat): ConverterUiState.Normal =
    ConverterUiState.Normal(
        engine = engine,
        value = if (engine.input.isEmpty()) "" else format.number(engine.input),
        result = ConverterEngine.result(engine)?.let(format::result).orEmpty(),
        decimalSeparator = format.decimalSeparator
    )
