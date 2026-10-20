// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the user turned animations off in the system settings ("Remove animations" or an
 * animator duration scale of 0). Decorative Himitsu animations must stay still when it is true.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Reads the system animator duration scale; needs no permission. */
internal fun Context.isReducedMotionEnabled(): Boolean {
    val scale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return scale == 0f
}
