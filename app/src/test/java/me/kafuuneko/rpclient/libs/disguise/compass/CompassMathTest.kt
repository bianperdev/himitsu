// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise.compass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompassMathTest {
    @Test
    fun directionsRoundToTheNearestOfEight() {
        assertEquals(Direction.North, CompassMath.direction(359f))
        assertEquals(Direction.North, CompassMath.direction(-10f))
        assertEquals(Direction.NorthEast, CompassMath.direction(40f))
        assertEquals(Direction.West, CompassMath.direction(271f))
        assertEquals(Direction.NorthWest, CompassMath.direction(320f))
    }

    @Test
    fun theFilterTakesTheShortWayAcrossNorth() {
        val filter = HeadingFilter(alpha())
        filter.update(350f)
        val next = filter.update(10f)
        // Halfway between 350° and 10° through north is 0°, not 180°
        assertTrue("got $next", next > 355f || next < 5f)
    }

    @Test
    fun aSequenceIsWrittenWithTheDialLetters() {
        val taps = listOf(CardinalPoint.North, CardinalPoint.West, CardinalPoint.West, CardinalPoint.South)
        assertEquals("NWWS", CompassMath.sequenceCode(taps))
    }

    private fun alpha() = 0.5f
}
