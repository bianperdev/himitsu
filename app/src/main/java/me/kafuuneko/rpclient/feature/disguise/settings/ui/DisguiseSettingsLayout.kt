// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.disguise.calculator.CALCULATOR_CODE_LENGTHS
import me.kafuuneko.rpclient.feature.disguise.calculator.presentation.CalculatorUiIntent
import me.kafuuneko.rpclient.feature.disguise.calculator.ui.CalculatorLayout
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseCodeError
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseSettingsUiIntent
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseSettingsUiState
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseSetupStep
import me.kafuuneko.rpclient.feature.disguise.ui.DisguiseTheme
import me.kafuuneko.rpclient.libs.disguise.Appearance
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.HimitsuPhonePreviews
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsSwitchTile
import me.kafuuneko.rpclient.ui.widgets.RpSettingsTile

/** Entry of the disguise settings screen. */
@Composable
fun DisguiseSettingsLayout(uiState: DisguiseSettingsUiState, emit: DisguiseSettingsUiIntent.() -> Unit) {
    BackHandler(enabled = uiState is DisguiseSettingsUiState.Normal) { DisguiseSettingsUiIntent.Back.emit() }
    when (uiState) {
        DisguiseSettingsUiState.None -> Unit
        is DisguiseSettingsUiState.Normal -> {
            val step = uiState.step
            if (step is DisguiseSetupStep.Practice) PracticeView(step, emit) else NormalView(uiState, emit)
            StepDialog(uiState, emit)
        }
        is DisguiseSettingsUiState.Finished -> DisguiseSettingsLayout(uiState.previous) { }
    }
}

@Composable
private fun NormalView(uiState: DisguiseSettingsUiState.Normal, emit: DisguiseSettingsUiIntent.() -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { AppTopBar(title = stringResource(R.string.disguise_settings_title), onBack = { DisguiseSettingsUiIntent.Back.emit() }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RpInfoCard(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.disguise_settings_about_title),
                subtitle = stringResource(R.string.disguise_settings_about_text)
            )
            if (!uiState.canEnable && !uiState.appearance.isDecoy) {
                RpSettingsGroup {
                    RpSettingsTile(
                        title = stringResource(R.string.disguise_settings_needs_lock_title),
                        subtitle = stringResource(R.string.disguise_settings_needs_lock_text),
                        icon = Icons.Rounded.Lock,
                        onClick = { DisguiseSettingsUiIntent.OpenSecuritySettings.emit() }
                    )
                }
            }
            DisguiseGroup(uiState, emit)
        }
    }
}

@Composable
private fun DisguiseGroup(uiState: DisguiseSettingsUiState.Normal, emit: DisguiseSettingsUiIntent.() -> Unit) {
    val enabled = uiState.appearance.isDecoy
    RpSettingsGroup {
        RpSettingsSwitchTile(
            title = stringResource(R.string.disguise_settings_switch),
            subtitle = if (enabled) stringResource(uiState.appearance.labelRes) else stringResource(R.string.disguise_settings_off),
            icon = Icons.Rounded.TheaterComedy,
            checked = enabled,
            enabled = (uiState.canEnable || enabled) && !uiState.isWorking,
            onCheckedChange = { on -> (if (on) DisguiseSettingsUiIntent.StartEnable else DisguiseSettingsUiIntent.StartDisable).emit() }
        )
        if (enabled) {
            if (!uiState.appLockEnabled) {
                RpSettingsDivider()
                RpSettingsTile(
                    title = stringResource(R.string.disguise_settings_lock_off_title),
                    subtitle = stringResource(R.string.disguise_settings_lock_off_text),
                    icon = Icons.Rounded.Warning,
                    iconColor = MaterialTheme.colorScheme.error,
                    iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                    onClick = { DisguiseSettingsUiIntent.OpenSecuritySettings.emit() }
                )
            }
            if (uiState.recentsPreviewVisible) {
                RpSettingsDivider()
                RpSettingsTile(
                    title = stringResource(R.string.disguise_settings_recents_title),
                    subtitle = stringResource(R.string.disguise_settings_recents_text, stringResource(R.string.security_settings_secure_screen)),
                    icon = Icons.Rounded.VisibilityOff,
                    onClick = { DisguiseSettingsUiIntent.OpenSecuritySettings.emit() }
                )
            }
            RpSettingsDivider()
            RpSettingsTile(
                title = stringResource(R.string.disguise_settings_recovery_title),
                subtitle = stringResource(R.string.disguise_change_recovery),
                icon = Icons.AutoMirrored.Rounded.HelpOutline
            )
        }
    }
}

/** A working calculator where the user types the new code once before it is applied. */
@Composable
private fun PracticeView(step: DisguiseSetupStep.Practice, emit: DisguiseSettingsUiIntent.() -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { AppTopBar(title = stringResource(R.string.disguise_practice_title), onBack = { DisguiseSettingsUiIntent.Cancel.emit() }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).fillMaxSize().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(if (step.matched) R.string.disguise_practice_done else R.string.disguise_practice_text),
                style = MaterialTheme.typography.bodyLarge,
                color = if (step.matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                DisguiseTheme {
                    Surface(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.large) {
                        CalculatorLayout(step.calculator) {
                            if (this is CalculatorUiIntent.Press) DisguiseSettingsUiIntent.PracticePress(key).emit()
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = { DisguiseSettingsUiIntent.Cancel.emit() }) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { DisguiseSettingsUiIntent.FinishPractice.emit() }, enabled = step.matched) {
                    Text(stringResource(R.string.disguise_practice_continue))
                }
            }
        }
    }
}

@Composable
private fun StepDialog(uiState: DisguiseSettingsUiState.Normal, emit: DisguiseSettingsUiIntent.() -> Unit) {
    when (val step = uiState.step) {
        is DisguiseSetupStep.EnterCode -> CodeDialog(step.error, emit)
        is DisguiseSetupStep.ConfirmChange -> AppearanceChangeDialog(
            target = step.target,
            previous = uiState.appearance,
            onConfirm = { DisguiseSettingsUiIntent.ConfirmChange.emit() },
            onDismiss = { DisguiseSettingsUiIntent.Cancel.emit() }
        )
        DisguiseSetupStep.None, is DisguiseSetupStep.Practice -> Unit
    }
}

/** Asks for the new code twice. The window is secure so the code never shows in screenshots. */
@Composable
private fun CodeDialog(error: DisguiseCodeError?, emit: DisguiseSettingsUiIntent.() -> Unit) {
    var code by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    val maxLength = CALCULATOR_CODE_LENGTHS.last
    AppDialogScaffold(
        onDismissRequest = { DisguiseSettingsUiIntent.Cancel.emit() },
        title = stringResource(R.string.disguise_code_title),
        subtitle = stringResource(R.string.disguise_code_hint),
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        onConfirm = { DisguiseSettingsUiIntent.SubmitCode(code, repeated).emit() }
    ) {
        CodeField(code, stringResource(R.string.disguise_code_field)) { code = it.filter(Char::isDigit).take(maxLength) }
        CodeField(repeated, stringResource(R.string.disguise_code_repeat_field)) { repeated = it.filter(Char::isDigit).take(maxLength) }
        if (error != null) {
            Text(
                text = stringResource(
                    when (error) {
                        DisguiseCodeError.Length -> R.string.disguise_code_error_length
                        DisguiseCodeError.Mismatch -> R.string.disguise_code_error_mismatch
                    }
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun CodeField(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth()
    )
}

@HimitsuPhonePreviews
@Composable
private fun DisguiseSettingsLayoutPreview() {
    AppTheme(dynamicColor = false) {
        DisguiseSettingsLayout(
            uiState = DisguiseSettingsUiState.Normal(appearance = Appearance.Calculator, appLockEnabled = true, deviceSecure = true),
            emit = {}
        )
    }
}
