// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.calculator.presentation

import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorError
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorState

/** State of the calculator decoy. */
sealed class CalculatorUiState {
    data object None : CalculatorUiState()

    /**
     * The display may hold the disguise code while it is typed, so [toString] never prints it.
     *
     * @property expression the formatted expression
     * @property preview the formatted running result, if any
     * @property decimalSeparator label of the decimal point key
     */
    data class Normal(
        val engine: CalculatorState,
        val expression: String,
        val preview: String?,
        val error: CalculatorError?,
        val decimalSeparator: Char
    ) : CalculatorUiState() {
        override fun toString(): String = "CalculatorUiState.Normal([REDACTED])"
    }
}
