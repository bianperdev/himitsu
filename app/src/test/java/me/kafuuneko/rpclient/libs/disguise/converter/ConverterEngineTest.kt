// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise.converter

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConverterEngineTest {
    private fun convert(value: String, from: MeasureUnit, to: MeasureUnit): BigDecimal =
        UnitConversion.convert(BigDecimal(value), from, to).stripTrailingZeros()

    private fun assertValue(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual.round(java.math.MathContext(12))))

    @Test
    fun linearUnitsConvertThroughTheirBaseUnit() {
        assertValue("1.609344", convert("1", MeasureUnit.Mile, MeasureUnit.Kilometre))
        assertValue("12", convert("1", MeasureUnit.Foot, MeasureUnit.Inch))
        assertValue("2.20462262185", convert("1", MeasureUnit.Kilogram, MeasureUnit.Pound))
        assertValue("100", convert("360", MeasureUnit.KilometrePerHour, MeasureUnit.MetrePerSecond))
        assertValue("1073741824", convert("1", MeasureUnit.Gibibyte, MeasureUnit.Byte))
    }

    @Test
    fun temperaturesUseTheirOffsets() {
        assertValue("212", convert("100", MeasureUnit.Celsius, MeasureUnit.Fahrenheit))
        assertValue("-40", convert("-40", MeasureUnit.Fahrenheit, MeasureUnit.Celsius))
        assertValue("-273.15", convert("0", MeasureUnit.Kelvin, MeasureUnit.Celsius))
        assertValue("32", convert("273.15", MeasureUnit.Kelvin, MeasureUnit.Fahrenheit))
    }

    @Test
    fun everyCategoryHasDistinctStableIds() {
        val ids = MeasureUnit.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ConverterCategory.entries.forEach { category ->
            category.units.forEach { unit -> UnitConversion.convert(BigDecimal.ONE, category.defaultPair.first, unit) }
        }
    }

    @Test
    fun typingFollowsTheKeypad() {
        var state = ConverterState()
        listOf(ConverterKey.Digit(0), ConverterKey.Digit(4), ConverterKey.DecimalPoint, ConverterKey.Digit(5), ConverterKey.ToggleSign)
            .forEach { state = ConverterEngine.press(state, it) }
        assertEquals("-4.5", state.input)
        state = ConverterEngine.press(ConverterEngine.press(state, ConverterKey.Delete), ConverterKey.Delete)
        assertEquals("-4", state.input)
        assertEquals("", ConverterEngine.press(state, ConverterKey.Clear).input)
    }

    @Test
    fun theShortcutIncludesTheUnitsBeforeTheSwap() {
        val state = ConverterState(ConverterCategory.Temperature, MeasureUnit.Fahrenheit, MeasureUnit.Kelvin, "482913")
        assertEquals("temperature:f:k:482913", ConverterEngine.shortcutCandidate(state, 4..10))
        assertNull(ConverterEngine.shortcutCandidate(state.copy(input = "4829.13"), 4..10))
        assertNull(ConverterEngine.shortcutCandidate(state.copy(input = "-482913"), 4..10))
        assertNull(ConverterEngine.shortcutCandidate(state.copy(input = "482"), 4..10))
        assertEquals(MeasureUnit.Kelvin, ConverterEngine.swap(state).from)
    }

    @Test
    fun changingCategoryResetsTheUnitsButKeepsTheValue() {
        val state = ConverterEngine.selectCategory(ConverterState(input = "12"), ConverterCategory.Mass)
        assertEquals(ConverterCategory.Mass.defaultPair, state.from to state.to)
        assertEquals("12", state.input)
        assertEquals(state, ConverterEngine.selectFrom(state, MeasureUnit.Kelvin))
    }
}
