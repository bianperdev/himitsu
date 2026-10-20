// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Arithmetic operators of the calculator decoy. */
enum class CalculatorOperator { Add, Subtract, Multiply, Divide }

/** A key of the calculator keypad. */
sealed interface CalculatorKey {
    data class Digit(val value: Int) : CalculatorKey {
        init {
            require(value in 0..9) { "Not a digit: $value" }
        }
    }

    data object DecimalPoint : CalculatorKey
    data class Operator(val operator: CalculatorOperator) : CalculatorKey
    data object Percent : CalculatorKey
    data object ToggleSign : CalculatorKey
    data object Delete : CalculatorKey
    data object Clear : CalculatorKey
    data object Equals : CalculatorKey
}

/** Part of the expression on the calculator display. */
sealed interface CalculatorToken {
    /**
     * A number: as typed (optional leading `-`, digits, at most one `.`), or a previous result in
     * `BigDecimal` notation, which may use an exponent.
     *
     * @property percent whether a `%` follows the number
     */
    data class Number(val text: String, val percent: Boolean = false) : CalculatorToken {
        /** The value, or null while the number is only a `-` waiting for digits. */
        val value: BigDecimal?
            get() = text.toBigDecimalOrNull()
    }

    data class Operator(val operator: CalculatorOperator) : CalculatorToken
}

/** Why an expression has no value. */
enum class CalculatorError { DivideByZero, TooLarge }

/** Outcome of evaluating a complete expression. */
sealed interface CalculatorResult {
    data class Value(val value: BigDecimal) : CalculatorResult
    data class Failure(val error: CalculatorError) : CalculatorResult
}

/**
 * What the calculator shows.
 *
 * The user may type the disguise code here, so [toString] never prints the expression.
 *
 * @property showsResult the expression is the result of "="; the next digit starts over
 * @property error the last "=" failed; the next digit starts over
 */
data class CalculatorState(
    val tokens: List<CalculatorToken> = emptyList(),
    val showsResult: Boolean = false,
    val error: CalculatorError? = null
) {
    override fun toString(): String = "CalculatorState([REDACTED])"
}

/**
 * The arithmetic of the calculator decoy: a pure reducer from key presses to display state.
 *
 * - Multiplication and division bind tighter than addition and subtraction.
 * - Values use `BigDecimal` with 20 significant digits while computing and 16 in results, so
 *   0.1 + 0.2 gives 0.3 and 1 ÷ 3 × 3 gives 1.
 * - `a + b%` and `a − b%` add or subtract b percent of a, as pocket calculators do; anywhere
 *   else `b%` is b ÷ 100.
 */
object CalculatorEngine {
    /** Digits a typed number may have. */
    const val MAX_DIGITS = 15

    private val WORKING_CONTEXT = MathContext(20, RoundingMode.HALF_EVEN)
    private val RESULT_CONTEXT = MathContext(16, RoundingMode.HALF_EVEN)
    private val HUNDRED = BigDecimal(100)
    private val LIMIT = BigDecimal("1E100")

    /** The state after pressing [key]. */
    fun press(state: CalculatorState, key: CalculatorKey): CalculatorState = when (key) {
        CalculatorKey.Clear -> CalculatorState()
        CalculatorKey.Delete -> delete(state)
        CalculatorKey.Equals -> equals(state)
        is CalculatorKey.Digit -> appendToNumber(state, '0' + key.value)
        CalculatorKey.DecimalPoint -> appendToNumber(state, '.')
        is CalculatorKey.Operator -> appendOperator(state, key.operator)
        CalculatorKey.Percent -> markPercent(state)
        CalculatorKey.ToggleSign -> toggleSign(state)
    }

    /** The running result shown under the expression, or null when there is nothing to preview. */
    fun preview(state: CalculatorState): BigDecimal? {
        if (state.showsResult || state.error != null) return null
        if (state.tokens.none { it is CalculatorToken.Operator || (it as? CalculatorToken.Number)?.percent == true }) return null
        return (evaluate(state.tokens) as? CalculatorResult.Value)?.value
    }

    /**
     * The typed number when the display holds only digits of a length in [lengths], which is
     * when "=" may be the disguise shortcut. A result of "=" never counts, only typed input.
     */
    fun shortcutCandidate(state: CalculatorState, lengths: IntRange): String? {
        if (state.showsResult || state.error != null) return null
        val number = state.tokens.singleOrNull() as? CalculatorToken.Number ?: return null
        if (number.percent || number.text.length !in lengths || !number.text.all { it in '0'..'9' }) return null
        return number.text
    }

    /**
     * Evaluates a whole expression.
     *
     * @return null while the expression is incomplete (a lone `-`, or two operators in a row)
     */
    fun evaluate(tokens: List<CalculatorToken>): CalculatorResult? {
        // Tokens must alternate number, operator, number
        if (tokens.size % 2 == 0) return null
        val numbers = tokens.filterIndexed { i, _ -> i % 2 == 0 }.map { it as? CalculatorToken.Number ?: return null }
        val operators = tokens.filterIndexed { i, _ -> i % 2 == 1 }.map { (it as? CalculatorToken.Operator)?.operator ?: return null }
        if (numbers.any { it.value == null }) return null
        var sum = BigDecimal.ZERO
        var sign = CalculatorOperator.Add
        var index = 0
        while (true) {
            // A term is a run of numbers joined by × and ÷
            val start = index
            var term = factor(numbers[index])
            while (index < operators.size && operators[index].isMultiplicative()) {
                val next = factor(numbers[index + 1])
                term = when (operators[index]) {
                    CalculatorOperator.Divide -> {
                        if (next.signum() == 0) return CalculatorResult.Failure(CalculatorError.DivideByZero)
                        term.divide(next, WORKING_CONTEXT)
                    }
                    else -> term.multiply(next, WORKING_CONTEXT)
                }
                index++
            }
            // "a + b%" means b percent of everything before it
            if (index == start && start > 0 && numbers[start].percent) {
                term = sum.multiply(numbers[start].value!!, WORKING_CONTEXT).divide(HUNDRED, WORKING_CONTEXT)
            }
            sum = if (sign == CalculatorOperator.Add) sum.add(term, WORKING_CONTEXT) else sum.subtract(term, WORKING_CONTEXT)
            if (index >= operators.size) break
            sign = operators[index]
            index++
        }
        if (sum.abs() >= LIMIT) return CalculatorResult.Failure(CalculatorError.TooLarge)
        return CalculatorResult.Value(sum)
    }

    /**
     * A result as number text: 16 significant digits without trailing zeros, in plain notation
     * unless it is very large or very small.
     */
    fun resultText(value: BigDecimal): String {
        val rounded = value.round(RESULT_CONTEXT).stripTrailingZeros()
        if (rounded.signum() == 0) return "0"
        val exponent = rounded.precision() - rounded.scale() - 1
        return if (exponent in -9..15) rounded.toPlainString() else rounded.toString()
    }

    private fun factor(number: CalculatorToken.Number): BigDecimal {
        val value = number.value!!
        return if (number.percent) value.divide(HUNDRED, WORKING_CONTEXT) else value
    }

    private fun CalculatorOperator.isMultiplicative(): Boolean =
        this == CalculatorOperator.Multiply || this == CalculatorOperator.Divide

    /** A digit or point after a result or an error starts a new expression. */
    private fun appendToNumber(state: CalculatorState, char: Char): CalculatorState {
        val base = if (state.showsResult || state.error != null) CalculatorState() else state
        val last = base.tokens.lastOrNull()
        if (last is CalculatorToken.Number) {
            if (last.percent) return base
            val text = appendChar(last.text, char) ?: return base
            return base.copy(tokens = base.tokens.dropLast(1) + CalculatorToken.Number(text))
        }
        val text = if (char == '.') "0." else char.toString()
        return base.copy(tokens = base.tokens + CalculatorToken.Number(text))
    }

    private fun appendChar(text: String, char: Char): String? {
        if ('E' in text) return null
        val sign = if (text.startsWith('-')) "-" else ""
        val body = text.removePrefix("-")
        if (char == '.') return if ('.' in body) null else sign + body.ifEmpty { "0" } + "."
        if (body.count { it.isDigit() } >= MAX_DIGITS) return null
        return if (body == "0") sign + char else sign + body + char
    }

    /** An operator continues from a result, replaces a previous operator, and starts from 0 on an empty display. */
    private fun appendOperator(state: CalculatorState, operator: CalculatorOperator): CalculatorState {
        if (state.error != null) return state
        val base = state.copy(showsResult = false)
        val token = CalculatorToken.Operator(operator)
        return when (val last = base.tokens.lastOrNull()) {
            null -> base.copy(tokens = listOf(CalculatorToken.Number("0"), token))
            is CalculatorToken.Operator -> base.copy(tokens = base.tokens.dropLast(1) + token)
            is CalculatorToken.Number -> if (last.value == null) base else base.copy(tokens = base.tokens + token)
        }
    }

    private fun markPercent(state: CalculatorState): CalculatorState {
        if (state.error != null) return state
        val last = state.tokens.lastOrNull() as? CalculatorToken.Number ?: return state
        if (last.percent || last.value == null) return state
        return state.copy(tokens = state.tokens.dropLast(1) + last.copy(percent = true), showsResult = false)
    }

    /** Toggles the sign of the last number, or starts a negative one after an operator. */
    private fun toggleSign(state: CalculatorState): CalculatorState {
        if (state.error != null) return state
        val last = state.tokens.lastOrNull()
        if (last !is CalculatorToken.Number) {
            return state.copy(tokens = state.tokens + CalculatorToken.Number("-"), showsResult = false)
        }
        val text = if (last.text.startsWith('-')) last.text.removePrefix("-") else "-" + last.text
        if (text.isEmpty()) return state.copy(tokens = state.tokens.dropLast(1))
        return state.copy(tokens = state.tokens.dropLast(1) + last.copy(text = text))
    }

    /** Deletes the last character; after a result or an error it clears everything. */
    private fun delete(state: CalculatorState): CalculatorState {
        if (state.showsResult || state.error != null) return CalculatorState()
        val last = state.tokens.lastOrNull() ?: return state
        val rest = state.tokens.dropLast(1)
        return when {
            last is CalculatorToken.Operator -> state.copy(tokens = rest)
            last is CalculatorToken.Number && last.percent -> state.copy(tokens = rest + last.copy(percent = false))
            last is CalculatorToken.Number && last.text.length > 1 && 'E' !in last.text ->
                state.copy(tokens = rest + last.copy(text = last.text.dropLast(1)))
            else -> state.copy(tokens = rest)
        }
    }

    /** Evaluates the expression, ignoring a trailing operator; an incomplete one is left as is. */
    private fun equals(state: CalculatorState): CalculatorState {
        if (state.error != null || state.showsResult) return state
        val tokens = if (state.tokens.lastOrNull() is CalculatorToken.Operator) state.tokens.dropLast(1) else state.tokens
        if (tokens.isEmpty()) return state
        return when (val result = evaluate(tokens)) {
            null -> state
            is CalculatorResult.Value -> CalculatorState(listOf(CalculatorToken.Number(resultText(result.value))), showsResult = true)
            is CalculatorResult.Failure -> state.copy(error = result.error)
        }
    }
}
