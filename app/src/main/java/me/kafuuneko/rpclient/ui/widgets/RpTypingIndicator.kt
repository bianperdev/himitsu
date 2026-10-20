// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.libs.core.ActivityPreview
import me.kafuuneko.rpclient.ui.theme.LocalReducedMotion

private const val DOT_COUNT = 3
private const val CYCLE_MILLIS = 1600
private const val DIM_ALPHA = 0.25f

/**
 * "Someone is typing" in Himitsu style: three square pixels that light up one by one, like
 * characters appearing on a terminal, then reset.
 *
 * - Without [color], the dots use the monitor violet, sakura pink and CRT cyan of the theme.
 * - With the system "remove animations" setting, the dots stay lit and still.
 *
 * @param color one color for every dot, for example the accent of the character who is typing
 */
@Composable
fun RpTypingIndicator(
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val colors = color?.let { List(DOT_COUNT) { color } } ?: listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    )
    val reducedMotion = LocalReducedMotion.current
    val litDots = if (reducedMotion) {
        DOT_COUNT.toFloat()
    } else {
        // Phase 0 shows no dot, then one more per step, so a cycle has DOT_COUNT + 1 states.
        val transition = rememberInfiniteTransition(label = "typing")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = DOT_COUNT + 1f,
            animationSpec = infiniteRepeatable(tween(CYCLE_MILLIS, easing = LinearEasing)),
            label = "typingPhase"
        )
        phase
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        colors.forEachIndexed { index, dotColor ->
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .alpha(if (litDots >= index + 1) 1f else DIM_ALPHA)
                    .background(dotColor, RoundedCornerShape(1.dp))
            )
        }
    }
}

@Preview
@Composable
private fun RpTypingIndicatorPreview() {
    ActivityPreview(darkTheme = true) {
        RpTypingIndicator()
    }
}
