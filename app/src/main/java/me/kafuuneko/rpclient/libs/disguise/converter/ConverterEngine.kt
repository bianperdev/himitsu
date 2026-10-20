// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise.converter

import java.math.BigDecimal
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorEngine

/** A key of the converter keypad. */
sealed interface ConverterKey {
    data class Digit(val value: Int) : ConverterKey {
        init {
            require(value in 0..9) { "Not a digit: $value" }
        }
    }

    data object DecimalPoint : ConverterKey
    data object ToggleSign : ConverterKey
    data object Delete : ConverterKey
    data object Clear : ConverterKey
}

/**
 * What the converter shows. The value may be the disguise code, so [toString] never prints it.
 *
 * @property input the value as typed: optional `-`, digits, at most one `.`
 */
data class ConverterState(
    val category: ConverterCategory = ConverterCategory.Length,
    val from: MeasureUnit = ConverterCategory.Length.defaultPair.first,
    val to: MeasureUnit = ConverterCategory.Length.defaultPair.second,
    val input: String = ""
) {
    override fun toString(): String = "ConverterState([REDACTED])"
}

/** Pure reducer of the unit converter decoy. */
object ConverterEngine {
    /** The state after a keypad press. */
    fun press(state: ConverterState, key: ConverterKey): ConverterState = when (key) {
        ConverterKey.Clear -> state.copy(input = "")
        ConverterKey.Delete -> state.copy(input = state.input.dropLast(1).let { if (it == "-") "" else it })
        ConverterKey.ToggleSign -> state.copy(input = if (state.input.startsWith('-')) state.input.drop(1) else "-" + state.input)
        ConverterKey.DecimalPoint -> if ('.' in state.input) state else state.copy(input = state.input.ifEmpty { "0" }.let { if (it == "-") "-0" else it } + ".")
        is ConverterKey.Digit -> appendDigit(state, '0' + key.value)
    }

    /** Opens [category] with its default units, keeping the typed value. */
    fun selectCategory(state: ConverterState, category: ConverterCategory): ConverterState {
        if (category == state.category) return state
        val (from, to) = category.defaultPair
        return state.copy(category = category, from = from, to = to)
    }

    fun selectFrom(state: ConverterState, unit: MeasureUnit): ConverterState =
        if (unit in state.category.units) state.copy(from = unit) else state

    fun selectTo(state: ConverterState, unit: MeasureUnit): ConverterState =
        if (unit in state.category.units) state.copy(to = unit) else state

    /** Swaps the two units, keeping the typed value. */
    fun swap(state: ConverterState): ConverterState = state.copy(from = state.to, to = state.from)

    /** The converted value, or null while nothing convertible is typed. */
    fun result(state: ConverterState): BigDecimal? {
        val value = state.input.toBigDecimalOrNull() ?: return null
        return UnitConversion.convert(value, state.from, state.to)
    }

    /**
     * The converter shortcut for the current state, `<category>:<from>:<to>:<digits>`, when the
     * typed value is only digits of a length in [lengths]; checked when the swap button is pressed.
     */
    fun shortcutCandidate(state: ConverterState, lengths: IntRange): String? {
        val input = state.input
        if (input.length !in lengths || !input.all { it in '0'..'9' }) return null
        return shortcutText(state.category, state.from, state.to, input)
    }

    /** The text the converter shortcut is derived from. */
    fun shortcutText(category: ConverterCategory, from: MeasureUnit, to: MeasureUnit, digits: String): String =
        "${category.id}:${from.id}:${to.id}:$digits"

    private fun appendDigit(state: ConverterState, char: Char): ConverterState {
        if (state.input.count { it.isDigit() } >= CalculatorEngine.MAX_DIGITS) return state
        val sign = if (state.input.startsWith('-')) "-" else ""
        val body = state.input.removePrefix("-")
        return state.copy(input = if (body == "0") sign + char else sign + body + char)
    }
}
