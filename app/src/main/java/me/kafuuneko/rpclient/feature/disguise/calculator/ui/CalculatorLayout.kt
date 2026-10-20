// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.calculator.ui

import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.disguise.calculator.presentation.CalculatorUiIntent
import me.kafuuneko.rpclient.feature.disguise.calculator.presentation.CalculatorUiState
import me.kafuuneko.rpclient.feature.disguise.ui.DisguiseTheme
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorEngine
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorError
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorFormat
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorKey
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorOperator
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorState
import me.kafuuneko.rpclient.ui.theme.HimitsuPhonePreviews

/** Visual group of a key, which sets its colors. */
private enum class KeyKind { Number, Operator, Edit, Equals }

/**
 * A keypad key.
 *
 * @property label text on the key, used when [icon] is null
 * @property descriptionRes spoken name for keys whose label is a symbol
 */
private data class KeySpec(
    val key: CalculatorKey,
    val kind: KeyKind,
    val label: String = "",
    val icon: ImageVector? = null,
    @param:StringRes val descriptionRes: Int? = null
)

/** The calculator decoy: a display and a 4 × 5 keypad. */
@Composable
fun CalculatorLayout(uiState: CalculatorUiState, emit: CalculatorUiIntent.() -> Unit) {
    val state = uiState as? CalculatorUiState.Normal ?: return
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CalculatorDisplay(state, Modifier.weight(1f).fillMaxWidth())
        CalculatorKeypad(state.decimalSeparator, emit, Modifier.weight(1.6f).fillMaxWidth())
    }
}

@Composable
private fun CalculatorDisplay(state: CalculatorUiState.Normal, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.End) {
        BasicText(
            text = state.expression.ifEmpty { "0" },
            style = MaterialTheme.typography.displayLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            ),
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 64.sp),
            modifier = Modifier.fillMaxWidth()
        )
        val error = state.error
        Text(
            text = if (error != null) stringResource(errorRes(error)) else state.preview.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

@Composable
private fun CalculatorKeypad(decimalSeparator: Char, emit: CalculatorUiIntent.() -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        keypadRows(decimalSeparator).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { spec ->
                    CalculatorButton(
                        spec = spec,
                        onPress = { CalculatorUiIntent.Press(spec.key).emit() },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun CalculatorButton(spec: KeySpec, onPress: () -> Unit, modifier: Modifier) {
    val view = LocalView.current
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (spec.kind) {
        KeyKind.Number -> colors.surfaceContainerHigh to colors.onSurface
        KeyKind.Operator -> colors.secondaryContainer to colors.onSecondaryContainer
        KeyKind.Edit -> colors.tertiaryContainer to colors.onTertiaryContainer
        KeyKind.Equals -> colors.primary to colors.onPrimary
    }
    Surface(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onPress()
        },
        shape = RoundedCornerShape(22.dp),
        color = container,
        contentColor = content,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            val description = spec.descriptionRes?.let { stringResource(it) }
            val labelModifier = if (description != null) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier
            if (spec.icon != null) {
                Icon(spec.icon, contentDescription = null, modifier = labelModifier.size(28.dp))
            } else {
                Text(spec.label, fontSize = 28.sp, color = Color.Unspecified, modifier = labelModifier)
            }
        }
    }
}

private fun keypadRows(decimalSeparator: Char): List<List<KeySpec>> {
    fun digit(value: Int) = KeySpec(CalculatorKey.Digit(value), KeyKind.Number, value.toString())
    fun operator(operator: CalculatorOperator, @StringRes description: Int) =
        KeySpec(CalculatorKey.Operator(operator), KeyKind.Operator, CalculatorFormat.symbol(operator), descriptionRes = description)
    return listOf(
        listOf(
            KeySpec(CalculatorKey.Clear, KeyKind.Edit, "C", descriptionRes = R.string.disguise_calc_clear),
            KeySpec(CalculatorKey.Delete, KeyKind.Edit, icon = Icons.AutoMirrored.Rounded.Backspace, descriptionRes = R.string.disguise_calc_delete),
            KeySpec(CalculatorKey.Percent, KeyKind.Operator, "%", descriptionRes = R.string.disguise_calc_percent),
            operator(CalculatorOperator.Divide, R.string.disguise_calc_divide)
        ),
        listOf(digit(7), digit(8), digit(9), operator(CalculatorOperator.Multiply, R.string.disguise_calc_multiply)),
        listOf(digit(4), digit(5), digit(6), operator(CalculatorOperator.Subtract, R.string.disguise_calc_minus)),
        listOf(digit(1), digit(2), digit(3), operator(CalculatorOperator.Add, R.string.disguise_calc_plus)),
        listOf(
            KeySpec(CalculatorKey.ToggleSign, KeyKind.Number, "±", descriptionRes = R.string.disguise_calc_toggle_sign),
            digit(0),
            KeySpec(CalculatorKey.DecimalPoint, KeyKind.Number, decimalSeparator.toString(), descriptionRes = R.string.disguise_calc_decimal),
            KeySpec(CalculatorKey.Equals, KeyKind.Equals, "=", descriptionRes = R.string.disguise_calc_equals)
        )
    )
}

@StringRes
private fun errorRes(error: CalculatorError): Int = when (error) {
    CalculatorError.DivideByZero -> R.string.disguise_calc_error_divide_by_zero
    CalculatorError.TooLarge -> R.string.disguise_calc_error_too_large
}

@HimitsuPhonePreviews
@Composable
private fun CalculatorLayoutPreview() {
    val engine = listOf(
        CalculatorKey.Digit(1), CalculatorKey.Digit(2), CalculatorKey.Digit(5), CalculatorKey.Digit(0),
        CalculatorKey.Operator(CalculatorOperator.Multiply), CalculatorKey.Digit(3)
    ).fold(CalculatorState(), CalculatorEngine::press)
    DisguiseTheme {
        Surface {
            CalculatorLayout(
                uiState = CalculatorUiState.Normal(
                    engine = engine,
                    expression = "1,250 × 3",
                    preview = "3,750",
                    error = null,
                    decimalSeparator = '.'
                ),
                emit = {}
            )
        }
    }
}
