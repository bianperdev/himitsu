// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.converter.presentation

import me.kafuuneko.rpclient.libs.disguise.converter.ConverterState

/** State of the unit converter decoy. */
sealed class ConverterUiState {
    data object None : ConverterUiState()

    /**
     * The typed value may be the disguise code, so [toString] never prints it.
     *
     * @property value the typed value, formatted
     * @property result the converted value, formatted, or empty
     * @property decimalSeparator label of the decimal point key
     */
    data class Normal(
        val engine: ConverterState,
        val value: String,
        val result: String,
        val decimalSeparator: Char
    ) : ConverterUiState() {
        override fun toString(): String = "ConverterUiState.Normal([REDACTED])"
    }
}
