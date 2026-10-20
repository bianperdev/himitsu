// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise.calculator

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorFormatTest {
    private val expression = listOf(
        CalculatorToken.Number("-1234567.5"),
        CalculatorToken.Operator(CalculatorOperator.Multiply),
        CalculatorToken.Number("12", percent = true)
    )

    @Test
    fun usesTheSeparatorsOfTheLocale() {
        assertEquals("−1,234,567.5 × 12%", CalculatorFormat(Locale.US).expression(expression))
        assertEquals("−1.234.567,5 × 12%", CalculatorFormat(Locale.GERMANY).expression(expression))
    }

    @Test
    fun keepsWhatIsBeingTyped() {
        val format = CalculatorFormat(Locale.US)
        assertEquals("0.", format.number("0."))
        assertEquals("−", format.number("-"))
        assertEquals("1.5E20", format.number("1.5E+20"))
    }
}
