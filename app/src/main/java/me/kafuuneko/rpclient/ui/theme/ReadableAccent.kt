// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.ColorUtils

private const val MIN_TEXT_CONTRAST = 4.5
private const val LIGHTNESS_STEP = 0.03f

/**
 * Returns this accent with the same hue, lightened or darkened until text in it reaches
 * WCAG AA (4.5:1) on the current surface.
 *
 * Avatar and name colors come from data (a stored accent or a hash of the name), so they
 * cannot be tuned per theme in advance.
 */
@Composable
@ReadOnlyComposable
fun Color.readableAccent(): Color {
    val surface = MaterialTheme.colorScheme.surface
    return readableOn(surface)
}

/** Pure version of [readableAccent], adjusting lightness in HSL until the contrast is reached. */
internal fun Color.readableOn(background: Color): Color {
    val backgroundArgb = background.toArgb()
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    val step = if (background.luminance() < 0.5f) LIGHTNESS_STEP else -LIGHTNESS_STEP
    var candidate = toArgb()
    while (ColorUtils.calculateContrast(candidate, backgroundArgb) < MIN_TEXT_CONTRAST) {
        val next = hsl[2] + step
        if (next !in 0f..1f) break
        hsl[2] = next
        candidate = ColorUtils.HSLToColor(hsl)
    }
    return Color(candidate)
}
