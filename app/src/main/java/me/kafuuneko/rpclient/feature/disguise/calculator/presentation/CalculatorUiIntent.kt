// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.calculator.presentation

import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorKey

/** User actions on the calculator decoy. */
sealed class CalculatorUiIntent {
    data object Init : CalculatorUiIntent()
    data class Press(val key: CalculatorKey) : CalculatorUiIntent()
}
