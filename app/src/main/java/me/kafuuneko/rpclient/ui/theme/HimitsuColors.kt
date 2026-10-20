// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Himitsu colors that have no Material 3 role.
 *
 * - The warm desk lamp ([lamp]) is for warnings and occasional highlights, never for large areas.
 * - [success] marks positive states such as an available provider.
 * - [monitorGlow] is the soft violet edge used on the user's chat bubble and glowing outlines.
 * - [accents] are the per-theme values behind the semantic accent tokens in Color.kt.
 *
 * These stay on the Himitsu palette even when dynamic color is on, so warnings keep their meaning.
 */
@Immutable
data class HimitsuColors(
    val lamp: Color,
    val onLamp: Color,
    val lampContainer: Color,
    val onLampContainer: Color,
    val success: Color,
    val monitorGlow: Color,
    val accents: HimitsuAccents
)

/**
 * Accent hues for icons, charts and colored labels.
 *
 * Each one is readable as text (4.5:1) on every surface of its theme. A single value cannot
 * reach 4.5:1 on both the night and the morning surfaces, so dark and light get their own set.
 */
@Immutable
data class HimitsuAccents(
    val sky: Color,
    val emerald: Color,
    val indigo: Color,
    val pink: Color,
    val amber: Color,
    val blue: Color,
    val violet: Color,
    val red: Color,
    val bronze: Color,
    val silver: Color
)

internal val HimitsuDarkColors = HimitsuColors(
    lamp = Color(0xFFFFB547),
    onLamp = Color(0xFF3D2600),
    lampContainer = Color(0xFF5A3C08),
    onLampContainer = Color(0xFFFFDDB0),
    success = Color(0xFF7BE0A8),
    monitorGlow = MonitorViolet.copy(alpha = 0.38f),
    accents = HimitsuAccents(
        sky = CrtCyan,
        emerald = Color(0xFF7BE0A8),
        indigo = Color(0xFF9FA8FF),
        pink = SakuraNeon,
        amber = Color(0xFFFFB547),
        blue = Color(0xFF7FB2FF),
        violet = Color(0xFFB79BFF),
        red = AlertRose,
        bronze = Color(0xFFF0A877),
        silver = Color(0xFFC9C2D6)
    )
)

internal val HimitsuLightColors = HimitsuColors(
    lamp = Color(0xFF8A5300),
    onLamp = Color.White,
    lampContainer = Color(0xFFFFDDB0),
    onLampContainer = Color(0xFF2C1700),
    success = Color(0xFF1E6B45),
    monitorGlow = DayViolet.copy(alpha = 0.30f),
    accents = HimitsuAccents(
        sky = DayCrt,
        emerald = Color(0xFF1E6B45),
        indigo = Color(0xFF4A4FC4),
        pink = DaySakura,
        amber = Color(0xFF8A5300),
        blue = Color(0xFF2D5BB8),
        violet = DayViolet,
        red = DayAlert,
        bronze = Color(0xFF8B4A1F),
        silver = Color(0xFF5A536A)
    )
)

internal val LocalHimitsuColors = staticCompositionLocalOf { HimitsuDarkColors }

/** Himitsu-specific colors of the current theme, next to [MaterialTheme.colorScheme]. */
val MaterialTheme.himitsuColors: HimitsuColors
    @Composable
    @ReadOnlyComposable
    get() = LocalHimitsuColors.current
