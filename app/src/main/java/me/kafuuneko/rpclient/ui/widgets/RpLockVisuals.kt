// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.libs.core.ActivityPreview
import me.kafuuneko.rpclient.ui.theme.himitsuColors

/*
 * Himitsu styling pieces for the app lock screen. The screen itself and its logic live in
 * `feature/lock` (feat/security); these only draw, hold no text or branding, and have no animation,
 * so a disguise entry point can reuse them with a neutral icon.
 */

/**
 * The dark room at 3 a.m.: a soft monitor glow from above and a faint desk lamp from below,
 * drawn over whatever background the screen already has.
 */
@Composable
fun Modifier.lockRoomLight(): Modifier {
    val monitorGlow = Brush.radialGradient(
        colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), Color.Transparent),
        radius = 900f
    )
    val lampLight = Brush.verticalGradient(
        colors = listOf(Color.Transparent, MaterialTheme.himitsuColors.lamp.copy(alpha = 0.05f))
    )
    return this
        .background(monitorGlow)
        .background(lampLight)
}

/**
 * The lock screen mark: an icon on a softly glowing tile, like a screen in the dark.
 *
 * @param icon the symbol to show; a lock by default, a warning for error states
 */
@Composable
fun RpLockMark(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Lock
) {
    Surface(
        modifier = modifier.size(72.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.himitsuColors.monitorGlow)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Preview
@Composable
private fun RpLockMarkPreview() {
    ActivityPreview(darkTheme = true) {
        Column(modifier = Modifier.lockRoomLight().padding(24.dp)) {
            Row {
                RpLockMark()
                RpLockMark(modifier = Modifier.padding(start = 16.dp), icon = Icons.Rounded.WarningAmber)
            }
        }
    }
}
