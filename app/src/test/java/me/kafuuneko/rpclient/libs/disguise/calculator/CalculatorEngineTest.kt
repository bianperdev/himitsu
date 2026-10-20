// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEngineTest {
    private val codeLengths = 4..10

    /** Types [input], one character per key: digits, `.`, `+ - * /`, `%`, `~` (sign), `<` (delete), `C`, `=`. */
    private fun type(input: String, start: CalculatorState = CalculatorState()): CalculatorState =
        input.fold(start) { state, char -> CalculatorEngine.press(state, key(char)) }

    private fun key(char: Char): CalculatorKey = when (char) {
        in '0'..'9' -> CalculatorKey.Digit(char - '0')
        '.' -> CalculatorKey.DecimalPoint
        '+' -> CalculatorKey.Operator(CalculatorOperator.Add)
        '-' -> CalculatorKey.Operator(CalculatorOperator.Subtract)
        '*' -> CalculatorKey.Operator(CalculatorOperator.Multiply)
        '/' -> CalculatorKey.Operator(CalculatorOperator.Divide)
        '%' -> CalculatorKey.Percent
        '~' -> CalculatorKey.ToggleSign
        '<' -> CalculatorKey.Delete
        'C' -> CalculatorKey.Clear
        '=' -> CalculatorKey.Equals
        else -> error("No key for $char")
    }

    private fun result(input: String): String {
        val state = type(input)
        assertTrue("$input should show a result", state.showsResult)
        return (state.tokens.single() as CalculatorToken.Number).text
    }

    @Test
    fun multiplicationAndDivisionBindTighter() {
        assertEquals("14", result("2+3*4="))
        assertEquals("-2", result("2-8/2="))
        assertEquals("1", result("1/3*3="))
    }

    @Test
    fun decimalsAreExact() {
        assertEquals("0.3", result(".1+.2="))
        assertEquals("0.3333333333333333", result("1/3="))
        assertEquals("2.5", result("5/2="))
    }

    @Test
    fun percentAddsOrSubtractsAShareOfWhatCameBefore() {
        assertEquals("55", result("50+10%="))
        assertEquals("190", result("200-5%="))
        assertEquals("5", result("50*10%="))
        assertEquals("0.1", result("10%="))
    }

    @Test
    fun signTogglesTheLastNumberOrStartsANegativeOne() {
        assertEquals("-2", result("5~+3="))
        assertEquals("-15", result("5*~3="))
        assertEquals("5", result("5~~="))
    }

    @Test
    fun errorsAreReportedAndClearedByTheNextNumber() {
        val divided = type("7/0=")
        assertEquals(CalculatorError.DivideByZero, divided.error)
        assertEquals(listOf(CalculatorToken.Number("4")), type("4", divided).tokens)
        assertEquals(CalculatorError.TooLarge, type("999999999999999*999999999999999*999999999999999*999999999999999*999999999999999*999999999999999*999999999999999=").error)
    }

    @Test
    fun hugeAndTinyResultsUseAnExponent() {
        assertEquals("1E+20", result("10000000000*10000000000="))
        assertEquals("1E-10", result("1/10000000000="))
    }

    @Test
    fun editingFollowsTheUsualCalculatorRules() {
        assertEquals(listOf(CalculatorToken.Number("12")), type("123<").tokens)
        assertEquals(listOf(CalculatorToken.Number("5"), CalculatorToken.Operator(CalculatorOperator.Subtract)), type("5+-").tokens)
        assertEquals(listOf(CalculatorToken.Number("0"), CalculatorToken.Operator(CalculatorOperator.Multiply)), type("*").tokens)
        assertEquals(listOf(CalculatorToken.Number("7")), type("007").tokens)
        assertEquals(listOf(CalculatorToken.Number("0.5")), type(".5..").tokens)
        assertEquals(CalculatorState(), type("12+3C"))
        assertEquals("8", result("5+3+="))
    }

    @Test
    fun aResultIsReplacedByANewNumberButContinuedByAnOperator() {
        assertEquals(listOf(CalculatorToken.Number("9")), type("2+2=9").tokens)
        assertEquals("8", result("2+2=*2="))
        assertEquals(CalculatorState(), type("2+2=<"))
    }

    @Test
    fun numbersStopAtTheDigitLimit() {
        val typed = type("1234567890123456789")
        assertEquals("123456789012345", (typed.tokens.single() as CalculatorToken.Number).text)
    }

    @Test
    fun previewShowsTheRunningResultOnlyForExpressions() {
        assertEquals(0, CalculatorEngine.preview(type("12*3"))!!.compareTo(36.toBigDecimal()))
        assertNull(CalculatorEngine.preview(type("12")))
        assertNull(CalculatorEngine.preview(type("12*")))
        assertNull(CalculatorEngine.preview(type("1/0")))
    }

    @Test
    fun onlyATypedPlainNumberIsAShortcutCandidate() {
        assertEquals("482913", CalculatorEngine.shortcutCandidate(type("482913"), codeLengths))
        assertNull(CalculatorEngine.shortcutCandidate(type("482"), codeLengths))
        assertNull(CalculatorEngine.shortcutCandidate(type("48291348291"), codeLengths))
        assertNull(CalculatorEngine.shortcutCandidate(type("4829.13"), codeLengths))
        assertNull(CalculatorEngine.shortcutCandidate(type("482913~"), codeLengths))
        assertNull(CalculatorEngine.shortcutCandidate(type("482913%"), codeLengths))
        assertNull(CalculatorEngine.shortcutCandidate(type("4829+13"), codeLengths))
        // A result of "=" is not typed input, even when it looks like a code
        assertNull(CalculatorEngine.shortcutCandidate(type("482900+13="), codeLengths))
    }

    @Test
    fun equalsOnATypedNumberBehavesLikeAnyCalculator() {
        val state = type("482913=")
        assertTrue(state.showsResult)
        assertEquals(listOf(CalculatorToken.Number("482913")), state.tokens)
        assertFalse(state.toString().contains("482913"))
    }
}
