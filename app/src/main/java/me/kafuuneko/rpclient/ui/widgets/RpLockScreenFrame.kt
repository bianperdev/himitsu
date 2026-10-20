// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.libs.core.ActivityPreview
import me.kafuuneko.rpclient.ui.theme.himitsuColors

/**
 * Visual frame for the app lock screen: the dark room lit by the monitor, a lock mark, a title
 * and a slot for the unlock controls. It only draws; authentication belongs to `feat/security`.
 *
 * - No text or branding is built in: callers pass localized strings, and the disguise track can
 *   pass a neutral [mark] so nothing on screen reveals the app.
 * - It uses theme tokens only, so it follows dark, light and dynamic color.
 * - It has no animation.
 *
 * @param title main line, for example "Locked"
 * @param subtitle optional second line, for example why the app is locked or an error
 * @param mark the symbol above the title; a glowing lock by default
 * @param actions unlock controls, laid out in a centered column below the text
 */
@Composable
fun RpLockScreenFrame(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    mark: @Composable () -> Unit = { RpLockMark() },
    actions: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    // Monitor glow from above and a faint desk lamp from the lower corner.
    val roomLight = Brush.radialGradient(
        colors = listOf(colors.primary.copy(alpha = 0.16f), Color.Transparent),
        radius = 900f
    )
    val lampLight = Brush.verticalGradient(
        colors = listOf(Color.Transparent, MaterialTheme.himitsuColors.lamp.copy(alpha = 0.05f))
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .background(roomLight)
            .background(lampLight)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            mark()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onBackground,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            actions()
        }
    }
}

/** The default lock mark: a lock on a softly glowing tile, like a screen in the dark. */
@Composable
fun RpLockMark(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(72.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.himitsuColors.monitorGlow)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 720)
@Composable
private fun RpLockScreenFrameDarkPreview() {
    ActivityPreview(darkTheme = true) {
        RpLockScreenFrame(title = "Locked", subtitle = "Unlock to continue") {
            Button(onClick = {}) { Text("Unlock") }
        }
    }
}

@Preview(widthDp = 390, heightDp = 720)
@Composable
private fun RpLockScreenFrameLightPreview() {
    ActivityPreview(darkTheme = false) {
        RpLockScreenFrame(title = "Locked", subtitle = "Unlock to continue") {
            Button(onClick = {}) { Text("Unlock") }
        }
    }
}
