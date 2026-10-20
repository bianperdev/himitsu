// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.converter.ui

import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.disguise.converter.converterUiState
import me.kafuuneko.rpclient.feature.disguise.converter.presentation.ConverterUiIntent
import me.kafuuneko.rpclient.feature.disguise.converter.presentation.ConverterUiState
import me.kafuuneko.rpclient.feature.disguise.ui.DisguiseTheme
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorFormat
import me.kafuuneko.rpclient.libs.disguise.converter.ConverterCategory
import me.kafuuneko.rpclient.libs.disguise.converter.ConverterKey
import me.kafuuneko.rpclient.libs.disguise.converter.ConverterState
import me.kafuuneko.rpclient.libs.disguise.converter.MeasureUnit
import me.kafuuneko.rpclient.ui.theme.HimitsuPhonePreviews

/** The unit converter decoy: categories, two unit rows with a swap button, and a keypad. */
@Composable
fun ConverterLayout(uiState: ConverterUiState, emit: ConverterUiIntent.() -> Unit) {
    val state = uiState as? ConverterUiState.Normal ?: return
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ConverterCategory.entries) { category ->
                FilterChip(
                    selected = category == state.engine.category,
                    onClick = { ConverterUiIntent.SelectCategory(category).emit() },
                    label = { Text(stringResource(categoryRes(category))) }
                )
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UnitRow(state.engine.category, state.engine.from, state.value.ifEmpty { "0" }, Modifier.weight(1f)) {
                ConverterUiIntent.SelectFrom(it).emit()
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FilledTonalIconButton(onClick = { ConverterUiIntent.Swap.emit() }) {
                    Icon(Icons.Rounded.SwapVert, contentDescription = stringResource(R.string.disguise_conv_swap))
                }
            }
            UnitRow(state.engine.category, state.engine.to, state.result.ifEmpty { "0" }, Modifier.weight(1f)) {
                ConverterUiIntent.SelectTo(it).emit()
            }
        }
        ConverterKeypad(state.decimalSeparator, emit, Modifier.weight(1.3f).fillMaxWidth().padding(horizontal = 16.dp))
    }
}

@Composable
private fun UnitRow(
    category: ConverterCategory,
    unit: MeasureUnit,
    value: String,
    modifier: Modifier,
    onSelect: (MeasureUnit) -> Unit
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            UnitPicker(category, unit, onSelect)
            BasicText(
                text = value,
                style = MaterialTheme.typography.displaySmall.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End),
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 40.sp),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun UnitPicker(category: ConverterCategory, unit: MeasureUnit, onSelect: (MeasureUnit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(unit.symbol, style = MaterialTheme.typography.titleLarge)
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            category.units.forEach { option ->
                DropdownMenuItem(text = { Text(option.symbol) }, onClick = {
                    expanded = false
                    onSelect(option)
                })
            }
        }
    }
}

/** A keypad key: a text label, or an icon with a spoken name. */
private data class ConverterKeySpec(
    val key: ConverterKey,
    val label: String = "",
    val icon: ImageVector? = null,
    @param:StringRes val descriptionRes: Int? = null,
    val weight: Float = 1f
)

@Composable
private fun ConverterKeypad(decimalSeparator: Char, emit: ConverterUiIntent.() -> Unit, modifier: Modifier) {
    fun digit(value: Int) = ConverterKeySpec(ConverterKey.Digit(value), value.toString())
    val rows = listOf(
        listOf(digit(7), digit(8), digit(9), ConverterKeySpec(ConverterKey.Clear, "C", descriptionRes = R.string.disguise_calc_clear)),
        listOf(digit(4), digit(5), digit(6), ConverterKeySpec(ConverterKey.Delete, icon = Icons.AutoMirrored.Rounded.Backspace, descriptionRes = R.string.disguise_calc_delete)),
        listOf(digit(1), digit(2), digit(3), ConverterKeySpec(ConverterKey.ToggleSign, "±", descriptionRes = R.string.disguise_calc_toggle_sign)),
        listOf(digit(0).copy(weight = 2f), ConverterKeySpec(ConverterKey.DecimalPoint, decimalSeparator.toString(), descriptionRes = R.string.disguise_calc_decimal))
    )
    val view = LocalView.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { spec ->
                    Surface(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            ConverterUiIntent.Press(spec.key).emit()
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = if (spec.key is ConverterKey.Digit) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.weight(spec.weight).fillMaxHeight()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val description = spec.descriptionRes?.let { stringResource(it) }
                            val labelModifier = if (description != null) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier
                            if (spec.icon != null) {
                                Icon(spec.icon, contentDescription = null, modifier = labelModifier.size(24.dp))
                            } else {
                                Text(spec.label, fontSize = 24.sp, modifier = labelModifier)
                            }
                        }
                    }
                }
            }
        }
    }
}

@StringRes
private fun categoryRes(category: ConverterCategory): Int = when (category) {
    ConverterCategory.Length -> R.string.disguise_conv_length
    ConverterCategory.Mass -> R.string.disguise_conv_mass
    ConverterCategory.Temperature -> R.string.disguise_conv_temperature
    ConverterCategory.Volume -> R.string.disguise_conv_volume
    ConverterCategory.Area -> R.string.disguise_conv_area
    ConverterCategory.Speed -> R.string.disguise_conv_speed
    ConverterCategory.Time -> R.string.disguise_conv_time
    ConverterCategory.Data -> R.string.disguise_conv_data
}

@HimitsuPhonePreviews
@Composable
private fun ConverterLayoutPreview() {
    DisguiseTheme {
        Surface {
            ConverterLayout(converterUiState(ConverterState(input = "12.5"), CalculatorFormat(Locale.US))) {}
        }
    }
}
