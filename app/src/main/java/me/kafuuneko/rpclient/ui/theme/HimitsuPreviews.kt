// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.ui.theme

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Renders a phone-sized preview twice: the dark "3 a.m." theme and the light "morning after" one.
 *
 * Use it with a theme that follows the system, such as `AppTheme(dynamicColor = false)` or
 * `ActivityPreview(darkTheme = isSystemInDarkTheme())`, so each preview picks up its `uiMode`.
 */
@Preview(
    name = "3 a.m. (dark)",
    widthDp = 390,
    heightDp = 844,
    showBackground = true,
    backgroundColor = 0xFF0E0B16,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Preview(
    name = "Morning after (light)",
    widthDp = 390,
    heightDp = 844,
    showBackground = true,
    backgroundColor = 0xFFF7F4FA,
    uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL
)
annotation class HimitsuPhonePreviews
