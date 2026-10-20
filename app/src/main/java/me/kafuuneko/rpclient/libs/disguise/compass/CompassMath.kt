// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise.compass

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The four letters on the compass dial, which are also the alphabet of the compass shortcut. */
enum class CardinalPoint(val degrees: Float, val code: Char) {
    North(0f, 'N'),
    East(90f, 'E'),
    South(180f, 'S'),
    West(270f, 'W')
}

/** The eight directions shown next to the heading. */
enum class Direction { North, NorthEast, East, SouthEast, South, SouthWest, West, NorthWest }

/** Pure compass math, kept apart from the sensors so it can be unit-tested. */
object CompassMath {
    /** Normalizes any angle to [0, 360). */
    fun normalize(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

    /** The nearest of the eight directions to [degrees]. */
    fun direction(degrees: Float): Direction = Direction.entries[(normalize(degrees) / 45f).roundToInt() % 8]

    /** The shortcut text for a sequence of taps on the dial letters, such as `NESW`. */
    fun sequenceCode(taps: List<CardinalPoint>): String = taps.map { it.code }.joinToString("")
}

/**
 * Smooths a heading so the needle does not tremble.
 *
 * It filters the sine and cosine instead of the angle, so going from 359° to 1° moves 2°, not
 * 358° the other way round.
 *
 * @param mAlpha weight of each new reading, between 0 (frozen) and 1 (no smoothing)
 */
class HeadingFilter(private val mAlpha: Float = 0.15f) {
    private var mSin = 0f
    private var mCos = 0f
    private var mStarted = false

    /** Adds a raw reading in degrees and returns the smoothed heading in [0, 360). */
    fun update(degrees: Float): Float {
        val radians = Math.toRadians(degrees.toDouble())
        val s = sin(radians).toFloat()
        val c = cos(radians).toFloat()
        if (mStarted) {
            mSin += mAlpha * (s - mSin)
            mCos += mAlpha * (c - mCos)
        } else {
            mSin = s
            mCos = c
            mStarted = true
        }
        return CompassMath.normalize(Math.toDegrees(atan2(mSin, mCos).toDouble()).toFloat())
    }
}
