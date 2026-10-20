// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Neutral blue-grey fallback for devices without dynamic color (before Android 12). */
private val DisguiseLightColors = lightColorScheme(
    primary = Color(0xFF3A6190),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondaryContainer = Color(0xFFDCE3EE),
    onSecondaryContainer = Color(0xFF151C25),
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1B1C1E),
    surface = Color(0xFFF8F9FA),
    onSurface = Color(0xFF1B1C1E),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceContainer = Color(0xFFECEEF1),
    surfaceContainerHigh = Color(0xFFE6E8EB),
    outline = Color(0xFF73777F)
)

private val DisguiseDarkColors = darkColorScheme(
    primary = Color(0xFFA2C9FF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF1F4976),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondaryContainer = Color(0xFF3D4651),
    onSecondaryContainer = Color(0xFFDCE3EE),
    tertiaryContainer = Color(0xFF5B3F1C),
    onTertiaryContainer = Color(0xFFFFDDB8),
    background = Color(0xFF1B1C1E),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF1B1C1E),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainer = Color(0xFF26282B),
    surfaceContainerHigh = Color(0xFF303235),
    outline = Color(0xFF8D9199)
)

/**
 * Theme of the decoy screens: a plain Material 3 look, never the Himitsu one.
 *
 * Decoys must look like generic system utilities, so they follow the wallpaper colors on
 * Android 12 and later, the system light or dark mode, and the system font.
 */
@Composable
fun DisguiseTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DisguiseDarkColors
        else -> DisguiseLightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
