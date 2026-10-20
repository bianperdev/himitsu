// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import me.kafuuneko.rpclient.R

/*
 * Bundled OFL-1.1 fonts (see NOTICE and LICENSES/). Nothing is downloaded at runtime.
 * They cover Latin, Vietnamese and Cyrillic; Chinese, Japanese and Korean glyphs fall back
 * to the system CJK font, which Android picks per device locale.
 */

private val TextWeights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

/**
 * Declares one entry per weight on a variable font file, so Android sets the `wght` axis instead of faking bold.
 *
 * `variationSettings` is still marked experimental in Compose; it works on every supported API level (26+).
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFonts(resId: Int, weights: List<FontWeight>, style: FontStyle = FontStyle.Normal): List<Font> {
    return weights.map { weight ->
        Font(
            resId = resId,
            weight = weight,
            style = style,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
        )
    }
}

/** Inter: the reading face for chats and body text, with a real italic for role-play *actions*. */
val HimitsuTextFamily: FontFamily = FontFamily(
    variableFonts(R.font.inter_variable, TextWeights) +
        variableFonts(R.font.inter_italic_variable, TextWeights, FontStyle.Italic)
)

/** Unbounded: the display face for large titles only; too wide for running text. */
val HimitsuDisplayFamily: FontFamily = FontFamily(
    variableFonts(R.font.unbounded_variable, listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold))
)

/** JetBrains Mono: times, token counts, code, JSON and Regex editors. */
val HimitsuMonoFamily: FontFamily = FontFamily(
    variableFonts(R.font.jetbrains_mono_variable, TextWeights)
)
