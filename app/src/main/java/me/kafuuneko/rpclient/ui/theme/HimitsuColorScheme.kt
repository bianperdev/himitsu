// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Himitsu palette: "a shut-in otaku's room at 3 a.m." for the dark theme and
 * "the morning after" for the light one. Every text/background pair used by the
 * schemes below meets WCAG AA (4.5:1 for text, 3:1 for outlines); the contrast
 * table lives in the aesthetic proposal referenced from ROADMAP.md.
 */

// Night: the room with the blinds down, lit only by the monitor.
internal val NightBase = Color(0xFF0E0B16)
internal val NightLowest = Color(0xFF09070F)
internal val NightLow = Color(0xFF130F1B)
internal val NightDesk = Color(0xFF17121F)
internal val NightShelf = Color(0xFF211A2C)
internal val NightHighest = Color(0xFF2B2338)
internal val NightBright = Color(0xFF342B42)
internal val NightText = Color(0xFFECE6F5)
internal val NightTextMuted = Color(0xFFA79FB8)
internal val NightOutline = Color(0xFF857C96)
internal val NightOutlineVariant = Color(0xFF3B3348)

internal val MonitorViolet = Color(0xFF9D7BFF)
internal val MonitorVioletInk = Color(0xFF1B0B4D)
internal val MonitorVioletDeep = Color(0xFF3B2A78)
internal val MonitorVioletPale = Color(0xFFE7DEFF)
internal val SakuraNeon = Color(0xFFFF6FB5)
internal val SakuraInk = Color(0xFF45082B)
internal val SakuraDeep = Color(0xFF5E1D44)
internal val SakuraPale = Color(0xFFFFD9EA)
internal val CrtCyan = Color(0xFF5CE1E6)
internal val CrtInk = Color(0xFF00373A)
internal val CrtDeep = Color(0xFF0D4C52)
internal val CrtPale = Color(0xFFB5F5F7)
internal val AlertRose = Color(0xFFFF5C7A)
internal val AlertRoseInk = Color(0xFF4B0014)
internal val AlertRoseDeep = Color(0xFF6E1A2D)
internal val AlertRosePale = Color(0xFFFFD9DF)

// Morning: blinds open, pale lavender daylight. Accents are darkened so white text still passes AA.
internal val DayBase = Color(0xFFF7F4FA)
internal val DayDim = Color(0xFFDED8E3)
internal val DayLow = Color(0xFFF3EFF7)
internal val DayContainer = Color(0xFFEEE9F3)
internal val DayHigh = Color(0xFFE8E2EE)
internal val DayHighest = Color(0xFFE2DCE9)
internal val DayPaper = Color(0xFFFFFFFF)
internal val DayText = Color(0xFF1D1726)
internal val DayTextMuted = Color(0xFF4D4659)
internal val DayOutline = Color(0xFF7A7287)
internal val DayOutlineVariant = Color(0xFFCDC5D6)

internal val DayViolet = Color(0xFF5B3FCF)
internal val DayVioletPale = Color(0xFFE6DEFF)
internal val DayVioletInk = Color(0xFF22106B)
internal val DaySakura = Color(0xFFA8326C)
internal val DaySakuraPale = Color(0xFFFFD9E8)
internal val DaySakuraInk = Color(0xFF3E0626)
internal val DayCrt = Color(0xFF00696D)
internal val DayCrtPale = Color(0xFFC4F1F2)
internal val DayCrtInk = Color(0xFF002022)
internal val DayAlert = Color(0xFFBA1A3F)
internal val DayAlertPale = Color(0xFFFFDADF)
internal val DayAlertInk = Color(0xFF40000F)

/**
 * Dark scheme, the core identity.
 *
 * `surface` is one step above `background` on purpose: the inherited screens use `surface`
 * as the card color, so making them equal (the Material 3 default) would hide cards.
 */
internal val HimitsuDarkColorScheme: ColorScheme = darkColorScheme(
    primary = MonitorViolet,
    onPrimary = MonitorVioletInk,
    primaryContainer = MonitorVioletDeep,
    onPrimaryContainer = MonitorVioletPale,
    inversePrimary = Color(0xFF6646D9),
    secondary = SakuraNeon,
    onSecondary = SakuraInk,
    secondaryContainer = SakuraDeep,
    onSecondaryContainer = SakuraPale,
    tertiary = CrtCyan,
    onTertiary = CrtInk,
    tertiaryContainer = CrtDeep,
    onTertiaryContainer = CrtPale,
    background = NightBase,
    onBackground = NightText,
    surface = NightDesk,
    onSurface = NightText,
    surfaceVariant = NightShelf,
    onSurfaceVariant = NightTextMuted,
    surfaceTint = MonitorViolet,
    inverseSurface = NightText,
    inverseOnSurface = Color(0xFF231D2D),
    error = AlertRose,
    onError = AlertRoseInk,
    errorContainer = AlertRoseDeep,
    onErrorContainer = AlertRosePale,
    outline = NightOutline,
    outlineVariant = NightOutlineVariant,
    scrim = Color.Black,
    surfaceBright = NightBright,
    surfaceContainer = NightDesk,
    surfaceContainerHigh = NightShelf,
    surfaceContainerHighest = NightHighest,
    surfaceContainerLow = NightLow,
    surfaceContainerLowest = NightLowest,
    surfaceDim = NightBase
)

/** Light scheme, "the morning after": calm and secondary to the dark theme. */
internal val HimitsuLightColorScheme: ColorScheme = lightColorScheme(
    primary = DayViolet,
    onPrimary = Color.White,
    primaryContainer = DayVioletPale,
    onPrimaryContainer = DayVioletInk,
    inversePrimary = Color(0xFFC9B8FF),
    secondary = DaySakura,
    onSecondary = Color.White,
    secondaryContainer = DaySakuraPale,
    onSecondaryContainer = DaySakuraInk,
    tertiary = DayCrt,
    onTertiary = Color.White,
    tertiaryContainer = DayCrtPale,
    onTertiaryContainer = DayCrtInk,
    background = DayBase,
    onBackground = DayText,
    surface = DayPaper,
    onSurface = DayText,
    surfaceVariant = DayHighest,
    onSurfaceVariant = DayTextMuted,
    surfaceTint = DayViolet,
    inverseSurface = Color(0xFF322B3B),
    inverseOnSurface = Color(0xFFF5EFFA),
    error = DayAlert,
    onError = Color.White,
    errorContainer = DayAlertPale,
    onErrorContainer = DayAlertInk,
    outline = DayOutline,
    outlineVariant = DayOutlineVariant,
    scrim = Color.Black,
    surfaceBright = DayBase,
    surfaceContainer = DayContainer,
    surfaceContainerHigh = DayHigh,
    surfaceContainerHighest = DayHighest,
    surfaceContainerLow = DayLow,
    surfaceContainerLowest = DayPaper,
    surfaceDim = DayDim
)
