// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise.converter

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Precision of every conversion step. */
private val CONTEXT = MathContext(20, RoundingMode.HALF_EVEN)

/**
 * A unit of measure.
 *
 * @property id stable identifier; it is part of the converter shortcut, so it never changes
 * @property symbol international symbol shown in the picker (symbols are not translated)
 * @property factor how many base units of its category make one of this unit; null for the
 *   temperature scales, which are affine
 */
enum class MeasureUnit(val id: String, val symbol: String, val factor: BigDecimal?) {
    Millimetre("mm", "mm", BigDecimal("0.001")),
    Centimetre("cm", "cm", BigDecimal("0.01")),
    Metre("m", "m", BigDecimal.ONE),
    Kilometre("km", "km", BigDecimal("1000")),
    Inch("in", "in", BigDecimal("0.0254")),
    Foot("ft", "ft", BigDecimal("0.3048")),
    Yard("yd", "yd", BigDecimal("0.9144")),
    Mile("mi", "mi", BigDecimal("1609.344")),

    Milligram("mg", "mg", BigDecimal("0.000001")),
    Gram("g", "g", BigDecimal("0.001")),
    Kilogram("kg", "kg", BigDecimal.ONE),
    Tonne("t", "t", BigDecimal("1000")),
    Ounce("oz", "oz", BigDecimal("0.028349523125")),
    Pound("lb", "lb", BigDecimal("0.45359237")),

    Celsius("c", "°C", null),
    Fahrenheit("f", "°F", null),
    Kelvin("k", "K", null),

    Millilitre("ml", "mL", BigDecimal("0.001")),
    Litre("l", "L", BigDecimal.ONE),
    CubicMetre("m3", "m³", BigDecimal("1000")),
    FluidOunce("floz", "fl oz", BigDecimal("0.0295735295625")),
    Gallon("gal", "gal", BigDecimal("3.785411784")),

    SquareCentimetre("cm2", "cm²", BigDecimal("0.0001")),
    SquareMetre("m2", "m²", BigDecimal.ONE),
    Hectare("ha", "ha", BigDecimal("10000")),
    SquareKilometre("km2", "km²", BigDecimal("1000000")),
    SquareFoot("ft2", "ft²", BigDecimal("0.09290304")),
    Acre("ac", "ac", BigDecimal("4046.8564224")),

    MetrePerSecond("mps", "m/s", BigDecimal.ONE),
    KilometrePerHour("kmh", "km/h", BigDecimal.ONE.divide(BigDecimal("3.6"), CONTEXT)),
    MilePerHour("mph", "mph", BigDecimal("0.44704")),
    Knot("kn", "kn", BigDecimal("1852").divide(BigDecimal("3600"), CONTEXT)),

    Millisecond("ms", "ms", BigDecimal("0.001")),
    Second("s", "s", BigDecimal.ONE),
    Minute("min", "min", BigDecimal("60")),
    Hour("h", "h", BigDecimal("3600")),
    Day("d", "d", BigDecimal("86400")),

    Bit("bit", "bit", BigDecimal("0.125")),
    Byte("b", "B", BigDecimal.ONE),
    Kilobyte("kb", "kB", BigDecimal("1000")),
    Megabyte("mb", "MB", BigDecimal("1000000")),
    Gigabyte("gb", "GB", BigDecimal("1000000000")),
    Terabyte("tb", "TB", BigDecimal("1000000000000")),
    Kibibyte("kib", "KiB", BigDecimal("1024")),
    Mebibyte("mib", "MiB", BigDecimal("1048576")),
    Gibibyte("gib", "GiB", BigDecimal("1073741824"))
}

/**
 * Categories of the unit converter, each with its units in picker order.
 *
 * @property id stable identifier; part of the converter shortcut
 */
enum class ConverterCategory(val id: String, val units: List<MeasureUnit>) {
    Length("length", listOf(MeasureUnit.Millimetre, MeasureUnit.Centimetre, MeasureUnit.Metre, MeasureUnit.Kilometre, MeasureUnit.Inch, MeasureUnit.Foot, MeasureUnit.Yard, MeasureUnit.Mile)),
    Mass("mass", listOf(MeasureUnit.Milligram, MeasureUnit.Gram, MeasureUnit.Kilogram, MeasureUnit.Tonne, MeasureUnit.Ounce, MeasureUnit.Pound)),
    Temperature("temperature", listOf(MeasureUnit.Celsius, MeasureUnit.Fahrenheit, MeasureUnit.Kelvin)),
    Volume("volume", listOf(MeasureUnit.Millilitre, MeasureUnit.Litre, MeasureUnit.CubicMetre, MeasureUnit.FluidOunce, MeasureUnit.Gallon)),
    Area("area", listOf(MeasureUnit.SquareCentimetre, MeasureUnit.SquareMetre, MeasureUnit.Hectare, MeasureUnit.SquareKilometre, MeasureUnit.SquareFoot, MeasureUnit.Acre)),
    Speed("speed", listOf(MeasureUnit.MetrePerSecond, MeasureUnit.KilometrePerHour, MeasureUnit.MilePerHour, MeasureUnit.Knot)),
    Time("time", listOf(MeasureUnit.Millisecond, MeasureUnit.Second, MeasureUnit.Minute, MeasureUnit.Hour, MeasureUnit.Day)),
    Data("data", listOf(MeasureUnit.Bit, MeasureUnit.Byte, MeasureUnit.Kilobyte, MeasureUnit.Megabyte, MeasureUnit.Gigabyte, MeasureUnit.Terabyte, MeasureUnit.Kibibyte, MeasureUnit.Mebibyte, MeasureUnit.Gibibyte));

    /** The two units a category opens with. */
    val defaultPair: Pair<MeasureUnit, MeasureUnit>
        get() = units[0] to units[1]
}

/** Unit conversion with `BigDecimal`. */
object UnitConversion {
    private val ABSOLUTE_ZERO_OFFSET = BigDecimal("273.15")
    private val NINE_FIFTHS = BigDecimal("1.8")
    private val THIRTY_TWO = BigDecimal("32")

    /** [value] in [from] expressed in [to]; both must belong to the same category. */
    fun convert(value: BigDecimal, from: MeasureUnit, to: MeasureUnit): BigDecimal {
        if (from == to) return value
        val fromFactor = from.factor
        val toFactor = to.factor
        if (fromFactor != null && toFactor != null) {
            return value.multiply(fromFactor, CONTEXT).divide(toFactor, CONTEXT)
        }
        return fromCelsius(toCelsius(value, from), to)
    }

    private fun toCelsius(value: BigDecimal, unit: MeasureUnit): BigDecimal = when (unit) {
        MeasureUnit.Celsius -> value
        MeasureUnit.Fahrenheit -> value.subtract(THIRTY_TWO).divide(NINE_FIFTHS, CONTEXT)
        MeasureUnit.Kelvin -> value.subtract(ABSOLUTE_ZERO_OFFSET)
        else -> error("Not a temperature unit: $unit")
    }

    private fun fromCelsius(celsius: BigDecimal, unit: MeasureUnit): BigDecimal = when (unit) {
        MeasureUnit.Celsius -> celsius
        MeasureUnit.Fahrenheit -> celsius.multiply(NINE_FIFTHS, CONTEXT).add(THIRTY_TWO)
        MeasureUnit.Kelvin -> celsius.add(ABSOLUTE_ZERO_OFFSET)
        else -> error("Not a temperature unit: $unit")
    }
}
