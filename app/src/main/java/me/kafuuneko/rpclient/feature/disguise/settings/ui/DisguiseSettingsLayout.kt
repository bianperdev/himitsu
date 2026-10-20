// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.disguise.calculator.CALCULATOR_CODE_LENGTHS
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseCodeError
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseSettingsUiIntent
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseSettingsUiState
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseSetupStep
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
            when (step) {
                is DisguiseSetupStep.PracticeCalculator -> PracticeCalculatorView(step, emit)
                is DisguiseSetupStep.PracticeConverter -> PracticeConverterView(step, emit)
                is DisguiseSetupStep.CompassSequence -> CompassSequenceView(step, emit)
                else -> NormalView(uiState, emit)
            }
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
            RpSettingsGroup {
                RpSettingsTile(
                    title = stringResource(R.string.disguise_settings_limits_title),
                    icon = Icons.Rounded.VisibilityOff,
                    onClick = { DisguiseSettingsUiIntent.ShowLimits.emit() }
                )
            }
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
            RpSettingsDivider()
            RpSettingsTile(
                title = stringResource(R.string.disguise_settings_change_title),
                subtitle = stringResource(R.string.disguise_settings_change_text),
                icon = Icons.Rounded.SwapHoriz,
                enabled = uiState.canEnable && !uiState.isWorking,
                onClick = { DisguiseSettingsUiIntent.StartChange.emit() }
            )
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
            RpSettingsSwitchTile(
                title = stringResource(R.string.disguise_settings_volume_title),
                subtitle = stringResource(R.string.disguise_settings_volume_text),
                icon = Icons.AutoMirrored.Rounded.VolumeDown,
                checked = uiState.quickExitVolumeKey,
                onCheckedChange = { DisguiseSettingsUiIntent.ToggleQuickExitVolumeKey(it).emit() }
            )
            RpSettingsDivider()
            RpSettingsTile(
                title = stringResource(R.string.disguise_settings_recovery_title),
                subtitle = stringResource(R.string.disguise_change_recovery),
                icon = Icons.AutoMirrored.Rounded.HelpOutline
            )
        }
    }
}

@Composable
private fun StepDialog(uiState: DisguiseSettingsUiState.Normal, emit: DisguiseSettingsUiIntent.() -> Unit) {
    when (val step = uiState.step) {
        is DisguiseSetupStep.ChooseDecoy -> ChooseDecoyDialog(step.selected, emit)
        DisguiseSetupStep.Limits -> LimitsDialog(emit)
        DisguiseSetupStep.Busy -> AppDialogScaffold(
            onDismissRequest = { DisguiseSettingsUiIntent.Cancel.emit() },
            title = stringResource(R.string.disguise_busy_title),
            subtitle = stringResource(R.string.disguise_busy_text),
            dismissText = null,
            confirmText = stringResource(R.string.disguise_about_close),
            onConfirm = { DisguiseSettingsUiIntent.Cancel.emit() }
        )
        is DisguiseSetupStep.EnterCode -> CodeDialog(step.error, emit)
        is DisguiseSetupStep.ConfirmChange -> AppearanceChangeDialog(
            target = step.target,
            previous = uiState.appearance,
            onConfirm = { DisguiseSettingsUiIntent.ConfirmChange.emit() },
            onDismiss = { DisguiseSettingsUiIntent.Cancel.emit() }
        )
        else -> Unit
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
