// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise.calculator

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formats calculator numbers and expressions for the display, with the decimal and grouping
 * separators of the user's locale.
 */
class CalculatorFormat(locale: Locale) {
    private val mSymbols = DecimalFormatSymbols.getInstance(locale)

    /** The decimal separator, which is also the label of the decimal point key. */
    val decimalSeparator: Char
        get() = mSymbols.decimalSeparator

    /** The whole expression, with typographic operator signs. */
    fun expression(tokens: List<CalculatorToken>): String = tokens.joinToString("") {
        when (it) {
            is CalculatorToken.Number -> number(it.text) + if (it.percent) "%" else ""
            is CalculatorToken.Operator -> " ${symbol(it.operator)} "
        }
    }

    /** A result value, rounded as the engine rounds results. */
    fun result(value: BigDecimal): String = number(CalculatorEngine.resultText(value))

    /** Number text as typed or as a result, keeping a trailing decimal separator while typing. */
    fun number(text: String): String {
        val sign = if (text.startsWith('-')) MINUS_SIGN else ""
        val body = text.removePrefix("-")
        if ('E' in body) return sign + body.replace('.', mSymbols.decimalSeparator).replace("E+", "E")
        val integer = body.substringBefore('.')
        val fraction = if ('.' in body) mSymbols.decimalSeparator + body.substringAfter('.') else ""
        return sign + group(integer) + fraction
    }

    private fun group(digits: String): String {
        val separator = mSymbols.groupingSeparator
        return digits.reversed().chunked(3).joinToString(separator.toString()).reversed()
    }

    companion object {
        private const val MINUS_SIGN = "−"

        /** The sign shown for [operator]. */
        fun symbol(operator: CalculatorOperator): String = when (operator) {
            CalculatorOperator.Add -> "+"
            CalculatorOperator.Subtract -> MINUS_SIGN
            CalculatorOperator.Multiply -> "×"
            CalculatorOperator.Divide -> "÷"
        }
    }
}
