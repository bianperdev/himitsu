// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.compass.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.disguise.compass.presentation.CompassUiIntent
import me.kafuuneko.rpclient.feature.disguise.compass.presentation.CompassUiState
import me.kafuuneko.rpclient.feature.disguise.ui.DisguiseTheme
import me.kafuuneko.rpclient.libs.disguise.compass.CardinalPoint
import me.kafuuneko.rpclient.libs.disguise.compass.CompassMath
import me.kafuuneko.rpclient.libs.disguise.compass.CompassMode
import me.kafuuneko.rpclient.libs.disguise.compass.Direction
import me.kafuuneko.rpclient.ui.theme.HimitsuPhonePreviews

/** The compass decoy: heading, a dial whose letters can be tapped, and a calibration hint. */
@Composable
fun CompassLayout(uiState: CompassUiState, emit: CompassUiIntent.() -> Unit) {
    val state = uiState as? CompassUiState.Normal ?: return
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
    ) {
        Reading(state)
        CompassDial(
            heading = if (state.mode == CompassMode.Compass) state.heading ?: 0f else 0f,
            showNeedle = state.mode == CompassMode.Compass,
            level = if (state.mode == CompassMode.Level) state.levelX to state.levelY else null,
            onTapPoint = { CompassUiIntent.TapPoint(it).emit() },
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()
        )
        if (state.mode == CompassMode.Compass && state.needsCalibration) {
            Notice(stringResource(R.string.disguise_compass_calibrate))
        }
    }
}

@Composable
private fun Reading(state: CompassUiState.Normal) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (state.mode) {
            CompassMode.Compass -> {
                val heading = state.heading
                Text(
                    text = if (heading == null) "—" else "${heading.roundToInt() % 360}°",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Light
                )
                Text(
                    text = heading?.let { stringResource(directionRes(CompassMath.direction(it))) }.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = stringResource(R.string.disguise_compass_magnetic),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CompassMode.Level, CompassMode.None -> {
                Text(
                    text = stringResource(R.string.disguise_compass_no_sensor),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                if (state.mode == CompassMode.Level) {
                    Text(
                        text = stringResource(R.string.disguise_compass_level),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * The dial. It turns so its N letter points to magnetic north while the marker at the top shows
 * where the phone points. Each letter is a tap target; taps are part of the shortcut and are
 * also used by the practice step in the disguise settings.
 *
 * @param level bubble position for the level, each axis between -1 and 1, or null
 */
@Composable
fun CompassDial(
    heading: Float,
    showNeedle: Boolean,
    level: Pair<Float, Float>?,
    onTapPoint: (CardinalPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        val radiusPx = with(LocalDensity.current) { (maxWidth / 2).toPx() }
        Canvas(Modifier.fillMaxSize().rotate(-heading)) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(colors.surfaceContainerHigh, radius = radiusPx * 0.98f, center = center)
            drawCircle(colors.outlineVariant, radius = radiusPx * 0.98f, center = center, style = Stroke(2.dp.toPx()))
            // A tick every 10°, longer every 30°
            for (degrees in 0 until 360 step 10) {
                val radians = Math.toRadians(degrees.toDouble() - 90)
                val inner = radiusPx * if (degrees % 30 == 0) 0.80f else 0.86f
                val outer = radiusPx * 0.92f
                drawLine(
                    color = if (degrees == 0) colors.error else colors.onSurfaceVariant,
                    start = Offset(center.x + inner * cos(radians).toFloat(), center.y + inner * sin(radians).toFloat()),
                    end = Offset(center.x + outer * cos(radians).toFloat(), center.y + outer * sin(radians).toFloat()),
                    strokeWidth = (if (degrees % 30 == 0) 3.dp else 1.5.dp).toPx()
                )
            }
            if (showNeedle) {
                val north = Path().apply {
                    moveTo(center.x, center.y - radiusPx * 0.55f)
                    lineTo(center.x + radiusPx * 0.07f, center.y)
                    lineTo(center.x - radiusPx * 0.07f, center.y)
                    close()
                }
                val south = Path().apply {
                    moveTo(center.x, center.y + radiusPx * 0.55f)
                    lineTo(center.x + radiusPx * 0.07f, center.y)
                    lineTo(center.x - radiusPx * 0.07f, center.y)
                    close()
                }
                drawPath(north, colors.error)
                drawPath(south, colors.outline)
                drawCircle(colors.surface, radius = radiusPx * 0.04f, center = center)
            }
        }
        // Fixed marker: where the top of the phone points
        Canvas(Modifier.fillMaxSize()) {
            val marker = Path().apply {
                moveTo(size.width / 2, 0f)
                lineTo(size.width / 2 + 8.dp.toPx(), 12.dp.toPx())
                lineTo(size.width / 2 - 8.dp.toPx(), 12.dp.toPx())
                close()
            }
            drawPath(marker, colors.primary)
        }
        CardinalPoint.entries.forEach { point ->
            DialLetter(point, heading, radiusPx * 0.66f, onTapPoint)
        }
        if (level != null) {
            val offsetPx = radiusPx * 0.5f
            Surface(
                modifier = Modifier
                    .offset { IntOffset((level.first * offsetPx).roundToInt(), (-level.second * offsetPx).roundToInt()) }
                    .size(40.dp),
                color = colors.primary.copy(alpha = 0.7f),
                shape = CircleShape
            ) {}
        }
    }
}

@Composable
private fun DialLetter(point: CardinalPoint, heading: Float, radiusPx: Float, onTapPoint: (CardinalPoint) -> Unit) {
    val radians = Math.toRadians((point.degrees - heading - 90).toDouble())
    val description = stringResource(pointNameRes(point))
    Box(
        Modifier
            .offset { IntOffset((radiusPx * cos(radians)).roundToInt(), (radiusPx * sin(radians)).roundToInt()) }
            .size(56.dp)
            .clip(CircleShape)
            .clickable { onTapPoint(point) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(directionRes(point.direction)),
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (point == CardinalPoint.North) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun Notice(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

private val CardinalPoint.direction: Direction
    get() = when (this) {
        CardinalPoint.North -> Direction.North
        CardinalPoint.East -> Direction.East
        CardinalPoint.South -> Direction.South
        CardinalPoint.West -> Direction.West
    }

@StringRes
private fun directionRes(direction: Direction): Int = when (direction) {
    Direction.North -> R.string.disguise_compass_dir_n
    Direction.NorthEast -> R.string.disguise_compass_dir_ne
    Direction.East -> R.string.disguise_compass_dir_e
    Direction.SouthEast -> R.string.disguise_compass_dir_se
    Direction.South -> R.string.disguise_compass_dir_s
    Direction.SouthWest -> R.string.disguise_compass_dir_sw
    Direction.West -> R.string.disguise_compass_dir_w
    Direction.NorthWest -> R.string.disguise_compass_dir_nw
}

@StringRes
private fun pointNameRes(point: CardinalPoint): Int = when (point) {
    CardinalPoint.North -> R.string.disguise_compass_north
    CardinalPoint.East -> R.string.disguise_compass_east
    CardinalPoint.South -> R.string.disguise_compass_south
    CardinalPoint.West -> R.string.disguise_compass_west
}

@HimitsuPhonePreviews
@Composable
private fun CompassLayoutPreview() {
    DisguiseTheme {
        Surface {
            CompassLayout(CompassUiState.Normal(mode = CompassMode.Compass, heading = 312f, needsCalibration = true)) {}
        }
    }
}
