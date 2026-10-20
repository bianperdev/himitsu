// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.disguise.calculator.presentation.CalculatorUiIntent
import me.kafuuneko.rpclient.feature.disguise.calculator.ui.CalculatorLayout
import me.kafuuneko.rpclient.feature.disguise.compass.COMPASS_SEQUENCE_LENGTHS
import me.kafuuneko.rpclient.feature.disguise.compass.ui.CompassDial
import me.kafuuneko.rpclient.feature.disguise.converter.ui.ConverterLayout
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseCodeError
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseSettingsUiIntent
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseSetupStep
import me.kafuuneko.rpclient.feature.disguise.ui.AppearanceIcon
import me.kafuuneko.rpclient.feature.disguise.ui.DisguiseTheme
import me.kafuuneko.rpclient.libs.disguise.Appearance
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.widgets.AppTopBar

/** Lets the user pick one of the three decoys, with the icon the launcher will show. */
@Composable
internal fun ChooseDecoyDialog(selected: Appearance, emit: DisguiseSettingsUiIntent.() -> Unit) {
    AppDialogScaffold(
        onDismissRequest = { DisguiseSettingsUiIntent.Cancel.emit() },
        title = stringResource(R.string.disguise_choose_title),
        onConfirm = { DisguiseSettingsUiIntent.ConfirmDecoy.emit() }
    ) {
        Appearance.entries.filter { it.isDecoy }.forEach { appearance ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = appearance == selected, role = Role.RadioButton) {
                        DisguiseSettingsUiIntent.SelectDecoy(appearance).emit()
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(selected = appearance == selected, onClick = null)
                AppearanceIcon(appearance, size = 40.dp, contentDescription = null)
                Text(stringResource(appearance.labelRes), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** A working calculator where the user types the new code once before it is applied. */
@Composable
internal fun PracticeCalculatorView(step: DisguiseSetupStep.PracticeCalculator, emit: DisguiseSettingsUiIntent.() -> Unit) {
    SetupScaffold(
        title = stringResource(R.string.disguise_practice_title),
        message = stringResource(if (step.matched) R.string.disguise_practice_done else R.string.disguise_practice_text),
        highlight = step.matched,
        confirmEnabled = step.matched,
        onConfirm = { DisguiseSettingsUiIntent.FinishPractice.emit() },
        emit = emit
    ) {
        DecoyFrame {
            CalculatorLayout(step.calculator) {
                if (this is CalculatorUiIntent.Press) DisguiseSettingsUiIntent.PracticePress(key).emit()
            }
        }
    }
}

/** A working converter where the user picks the units of the shortcut, types the code and swaps. */
@Composable
internal fun PracticeConverterView(step: DisguiseSetupStep.PracticeConverter, emit: DisguiseSettingsUiIntent.() -> Unit) {
    SetupScaffold(
        title = stringResource(R.string.disguise_practice_title),
        message = stringResource(if (step.matched) R.string.disguise_practice_done else R.string.disguise_practice_converter_text),
        highlight = step.matched,
        confirmEnabled = step.matched,
        onConfirm = { DisguiseSettingsUiIntent.FinishPractice.emit() },
        emit = emit
    ) {
        DecoyFrame {
            ConverterLayout(step.converter) { DisguiseSettingsUiIntent.PracticeConverter(this).emit() }
        }
    }
}

/** A still dial where the user taps the compass sequence, then repeats it. */
@Composable
internal fun CompassSequenceView(step: DisguiseSetupStep.CompassSequence, emit: DisguiseSettingsUiIntent.() -> Unit) {
    val error = when (step.error) {
        DisguiseCodeError.Length -> stringResource(R.string.disguise_sequence_error_length)
        DisguiseCodeError.Mismatch -> stringResource(R.string.disguise_sequence_error_mismatch)
        null -> null
    }
    SetupScaffold(
        title = stringResource(if (step.repeating) R.string.disguise_sequence_title_repeat else R.string.disguise_sequence_title),
        message = error ?: stringResource(R.string.disguise_sequence_text),
        isError = error != null,
        confirmText = stringResource(R.string.disguise_sequence_next),
        confirmEnabled = step.taps in COMPASS_SEQUENCE_LENGTHS,
        onConfirm = { DisguiseSettingsUiIntent.NextSequence.emit() },
        extraAction = stringResource(R.string.disguise_sequence_clear) to { DisguiseSettingsUiIntent.ClearSequence.emit() },
        emit = emit
    ) {
        DecoyFrame {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(
                    // Only the count is shown, never which letters were tapped
                    text = "●".repeat(step.taps) + "○".repeat((COMPASS_SEQUENCE_LENGTHS.first - step.taps).coerceAtLeast(0)),
                    style = MaterialTheme.typography.headlineMedium
                )
                CompassDial(
                    heading = 0f,
                    showNeedle = false,
                    level = null,
                    onTapPoint = { DisguiseSettingsUiIntent.TapSequence(it).emit() },
                    modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().padding(top = 16.dp)
                )
            }
        }
    }
}

/** The decoy inside the Himitsu settings, drawn with the decoy's own neutral theme. */
@Composable
private fun DecoyFrame(content: @Composable () -> Unit) {
    DisguiseTheme {
        Surface(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.large) { content() }
    }
}

/** Full screen step with a title bar, an instruction, the decoy and the actions. */
@Composable
private fun SetupScaffold(
    title: String,
    message: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    emit: DisguiseSettingsUiIntent.() -> Unit,
    highlight: Boolean = false,
    isError: Boolean = false,
    confirmText: String = stringResource(R.string.disguise_practice_continue),
    extraAction: Pair<String, () -> Unit>? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { AppTopBar(title = title, onBack = { DisguiseSettingsUiIntent.Cancel.emit() }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).fillMaxSize().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    isError -> MaterialTheme.colorScheme.error
                    highlight -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Box(Modifier.weight(1f).fillMaxWidth()) { Column { content() } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = { DisguiseSettingsUiIntent.Cancel.emit() }) { Text(stringResource(R.string.cancel)) }
                extraAction?.let { (label, action) -> TextButton(onClick = action) { Text(label) } }
                Button(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmText) }
            }
        }
    }
}
