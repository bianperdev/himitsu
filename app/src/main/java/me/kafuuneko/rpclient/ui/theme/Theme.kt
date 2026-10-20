package me.kafuuneko.rpclient.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/** 应用 Material 3 配色与字体；动态配色仅在调用方显式启用且系统支持时生效。 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Android 12 起系统才提供动态配色 API。
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && isDynamicColorSupported() -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> HimitsuDarkColorScheme
        else -> HimitsuLightColorScheme
    }
    // The glow follows the active primary so it also matches dynamic color.
    val himitsuColors = (if (darkTheme) HimitsuDarkColors else HimitsuLightColors).copy(
        monitorGlow = colorScheme.primary.copy(alpha = if (darkTheme) 0.38f else 0.30f)
    )

    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val reducedMotion = remember(context, inspection) { !inspection && context.isReducedMotionEnabled() }

    CompositionLocalProvider(
        LocalHimitsuColors provides himitsuColors,
        LocalReducedMotion provides reducedMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = HimitsuShapes
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                content = content
            )
        }
    }
}

/** Whether the system offers Material You dynamic color (Android 12 and later). */
fun isDynamicColorSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
