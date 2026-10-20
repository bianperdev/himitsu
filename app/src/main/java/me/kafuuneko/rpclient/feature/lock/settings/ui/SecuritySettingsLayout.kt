// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock.settings.ui

import androidx.activity.compose.BackHandler
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.lock.settings.presentation.RestoreCodeError
import me.kafuuneko.rpclient.feature.lock.settings.presentation.SecuritySettingsDialogState
import me.kafuuneko.rpclient.feature.lock.settings.presentation.SecuritySettingsUiIntent
import me.kafuuneko.rpclient.feature.lock.settings.presentation.SecuritySettingsUiState
import me.kafuuneko.rpclient.feature.lock.settings.presentation.SetAsideItem
import me.kafuuneko.rpclient.libs.security.lock.LockTimeout
import me.kafuuneko.rpclient.libs.security.storage.SetAsideKind
import me.kafuuneko.rpclient.ui.dialog.AppActionItem
import me.kafuuneko.rpclient.ui.dialog.AppActionListDialog
import me.kafuuneko.rpclient.ui.dialog.AppConfirmDialog
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.AppInputDialog
import me.kafuuneko.rpclient.ui.dialog.DialogBadgeTone
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.HimitsuMonoFamily
import me.kafuuneko.rpclient.ui.theme.HimitsuPhonePreviews
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsSwitchTile
import me.kafuuneko.rpclient.ui.widgets.RpSettingsTile
import me.kafuuneko.rpclient.ui.widgets.RpSettingsValueTile

/** Entry of the security settings screen. */
@Composable
fun SecuritySettingsLayout(
    uiState: SecuritySettingsUiState,
    emit: SecuritySettingsUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is SecuritySettingsUiState.Normal) { SecuritySettingsUiIntent.Back.emit() }
    when (uiState) {
        SecuritySettingsUiState.None -> Unit
        is SecuritySettingsUiState.Normal -> {
            NormalView(uiState, emit)
            DialogSwitch(uiState.dialogState, uiState.lockTimeout, emit)
        }
        is SecuritySettingsUiState.Finished -> SecuritySettingsLayout(uiState.previous) { }
    }
}

@Composable
private fun NormalView(uiState: SecuritySettingsUiState.Normal, emit: SecuritySettingsUiIntent.() -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.security_settings_title),
                onBack = { SecuritySettingsUiIntent.Back.emit() }
            )
        }
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
            LockSection(uiState, emit)
            ScreenSection(uiState, emit)
            RecoverySection(uiState, emit)
            if (uiState.setAside.isNotEmpty()) SetAsideSection(uiState, emit)
            AboutSection()
        }
    }
}

@Composable
private fun LockSection(uiState: SecuritySettingsUiState.Normal, emit: SecuritySettingsUiIntent.() -> Unit) {
    RpSectionHeader(title = stringResource(R.string.security_settings_section_lock))
    RpSettingsGroup {
        RpSettingsSwitchTile(
            title = stringResource(R.string.security_settings_app_lock),
            subtitle = stringResource(
                if (uiState.deviceSecure) R.string.security_settings_app_lock_desc
                else R.string.security_settings_app_lock_unavailable
            ),
            icon = Icons.Rounded.Lock,
            checked = uiState.appLockEnabled,
            enabled = uiState.deviceSecure || uiState.appLockEnabled,
            onCheckedChange = { SecuritySettingsUiIntent.ToggleAppLock(it).emit() }
        )
        RpSettingsDivider()
        RpSettingsValueTile(
            title = stringResource(R.string.security_settings_lock_timeout),
            value = stringResource(uiState.lockTimeout.labelRes()),
            icon = Icons.Rounded.Timer,
            enabled = uiState.appLockEnabled,
            onClick = { SecuritySettingsUiIntent.ShowTimeoutPicker.emit() }
        )
    }
}

@Composable
private fun ScreenSection(uiState: SecuritySettingsUiState.Normal, emit: SecuritySettingsUiIntent.() -> Unit) {
    RpSectionHeader(title = stringResource(R.string.security_settings_section_screen))
    RpSettingsGroup {
        RpSettingsSwitchTile(
            title = stringResource(R.string.security_settings_secure_screen),
            subtitle = stringResource(R.string.security_settings_secure_screen_desc),
            icon = Icons.Rounded.VisibilityOff,
            checked = uiState.secureScreenEnabled,
            onCheckedChange = { SecuritySettingsUiIntent.ToggleSecureScreen(it).emit() }
        )
    }
}

@Composable
private fun RecoverySection(uiState: SecuritySettingsUiState.Normal, emit: SecuritySettingsUiIntent.() -> Unit) {
    RpSectionHeader(title = stringResource(R.string.security_settings_section_recovery))
    RpSettingsGroup {
        RpSettingsTile(
            title = stringResource(
                if (uiState.hasRecoveryCode) R.string.security_settings_recovery_replace
                else R.string.security_settings_recovery_create
            ),
            subtitle = stringResource(
                if (uiState.hasRecoveryCode) R.string.security_settings_recovery_status_set
                else R.string.security_settings_recovery_status_none
            ),
            icon = Icons.Rounded.Key,
            enabled = !uiState.isWorking,
            onClick = { SecuritySettingsUiIntent.CreateRecoveryCode.emit() }
        )
        if (uiState.hasRecoveryCode) {
            RpSettingsDivider()
            RpSettingsTile(
                title = stringResource(R.string.security_settings_recovery_remove),
                icon = Icons.Rounded.Delete,
                iconColor = MaterialTheme.colorScheme.error,
                iconContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                enabled = !uiState.isWorking,
                onClick = { SecuritySettingsUiIntent.ShowRemoveRecoveryCodeDialog.emit() }
            )
        }
    }
    Text(
        text = stringResource(R.string.security_settings_recovery_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun SetAsideSection(uiState: SecuritySettingsUiState.Normal, emit: SecuritySettingsUiIntent.() -> Unit) {
    RpSectionHeader(title = stringResource(R.string.security_settings_section_set_aside))
    RpSettingsGroup {
        uiState.setAside.forEachIndexed { index, item ->
            if (index > 0) RpSettingsDivider()
            RpSettingsTile(
                title = stringResource(item.kind.labelRes()),
                subtitle = item.details(),
                icon = Icons.Rounded.Archive,
                enabled = !uiState.isWorking,
                onClick = { SecuritySettingsUiIntent.ShowSetAsideActions(item.suffix).emit() }
            )
        }
    }
    Text(
        text = stringResource(R.string.security_settings_set_aside_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

/** Date and size of a set, formatted for the device locale. */
@Composable
private fun SetAsideItem.details(): String {
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, sizeBytes)
    val date = time?.let { DateFormat.getMediumDateFormat(context).format(it) + " " + DateFormat.getTimeFormat(context).format(it) }
    return if (date == null) size else stringResource(R.string.security_set_aside_details, date, size)
}

private fun SetAsideKind.labelRes(): Int = when (this) {
    SetAsideKind.Unrecoverable -> R.string.security_set_aside_kind_unrecoverable
    SetAsideKind.Unusable -> R.string.security_set_aside_kind_unusable
    SetAsideKind.Replaced -> R.string.security_set_aside_kind_replaced
}

@Composable
private fun AboutSection() {
    RpSettingsGroup {
        RpSettingsTile(
            title = stringResource(R.string.security_settings_about_title),
            subtitle = stringResource(R.string.security_settings_about_text),
            icon = Icons.Rounded.Info
        )
    }
}

@Composable
private fun DialogSwitch(
    dialogState: SecuritySettingsDialogState,
    currentTimeout: LockTimeout,
    emit: SecuritySettingsUiIntent.() -> Unit
) {
    val dismiss = { SecuritySettingsUiIntent.DismissDialog.emit() }
    when (dialogState) {
        SecuritySettingsDialogState.None -> Unit
        SecuritySettingsDialogState.TimeoutPicker -> AppActionListDialog(
            onDismissRequest = dismiss,
            title = stringResource(R.string.security_settings_lock_timeout),
            badgeIcon = Icons.Rounded.Timer,
            actions = LockTimeout.entries.map { timeout ->
                AppActionItem(
                    icon = Icons.Rounded.Timer,
                    title = stringResource(timeout.labelRes()),
                    enabled = timeout != currentTimeout,
                    onClick = { SecuritySettingsUiIntent.SelectTimeout(timeout).emit() }
                )
            }
        )
        SecuritySettingsDialogState.ConfirmReplaceRecoveryCode -> AppConfirmDialog(
            onDismissRequest = dismiss,
            title = stringResource(R.string.security_recovery_replace_title),
            message = stringResource(R.string.security_recovery_replace_message),
            badgeIcon = Icons.Rounded.Key,
            badgeTone = DialogBadgeTone.Warning,
            onConfirm = { SecuritySettingsUiIntent.ConfirmReplaceRecoveryCode.emit() }
        )
        SecuritySettingsDialogState.ConfirmRemoveRecoveryCode -> AppConfirmDialog(
            onDismissRequest = dismiss,
            title = stringResource(R.string.security_recovery_remove_title),
            message = stringResource(R.string.security_recovery_remove_message),
            badgeIcon = Icons.Rounded.Delete,
            badgeTone = DialogBadgeTone.Danger,
            confirmText = stringResource(R.string.security_recovery_remove_confirm),
            confirmIsDestructive = true,
            onConfirm = { SecuritySettingsUiIntent.ConfirmRemoveRecoveryCode.emit() }
        )
        is SecuritySettingsDialogState.ShowRecoveryCode -> RecoveryCodeDialog(dialogState.code, dismiss)
        is SecuritySettingsDialogState.SetAsideActions -> SetAsideActionsDialog(dialogState, dismiss, emit)
        is SecuritySettingsDialogState.ConfirmRestoreSetAside -> AppConfirmDialog(
            onDismissRequest = dismiss,
            title = stringResource(R.string.security_restore_title),
            message = stringResource(R.string.security_restore_message),
            badgeIcon = Icons.Rounded.Restore,
            badgeTone = DialogBadgeTone.Warning,
            confirmText = stringResource(R.string.security_restore_confirm),
            onConfirm = { SecuritySettingsUiIntent.ConfirmRestoreSetAside.emit() }
        )
        is SecuritySettingsDialogState.RestoreCodeInput -> RestoreCodeDialog(dialogState, emit)
        is SecuritySettingsDialogState.ConfirmDeleteSetAside -> AppConfirmDialog(
            onDismissRequest = dismiss,
            title = stringResource(R.string.security_set_aside_delete_title),
            message = stringResource(R.string.security_set_aside_delete_message),
            badgeIcon = Icons.Rounded.Delete,
            badgeTone = DialogBadgeTone.Danger,
            confirmText = stringResource(R.string.security_set_aside_delete_confirm),
            confirmEnabled = dialogState.understood,
            confirmIsDestructive = true,
            onConfirm = { SecuritySettingsUiIntent.ConfirmDeleteSetAside.emit() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { SecuritySettingsUiIntent.SetDeleteUnderstood(!dialogState.understood).emit() }
            ) {
                Checkbox(
                    checked = dialogState.understood,
                    onCheckedChange = { SecuritySettingsUiIntent.SetDeleteUnderstood(it).emit() }
                )
                Text(
                    text = stringResource(R.string.security_set_aside_delete_understood),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Actions on a set-aside copy. The list closes itself after an action; that close is ignored,
 * so the dialog the action opened (the delete confirmation) stays.
 */
@Composable
private fun SetAsideActionsDialog(
    dialogState: SecuritySettingsDialogState.SetAsideActions,
    dismiss: () -> Unit,
    emit: SecuritySettingsUiIntent.() -> Unit
) {
    var chosen = false
    AppActionListDialog(
        onDismissRequest = { if (chosen) chosen = false else dismiss() },
        title = stringResource(dialogState.item.kind.labelRes()),
        subtitle = dialogState.item.details(),
        badgeIcon = Icons.Rounded.Archive,
        actions = listOfNotNull(
            AppActionItem(
                icon = Icons.Rounded.Restore,
                title = stringResource(R.string.security_set_aside_restore),
                onClick = {
                    chosen = true
                    SecuritySettingsUiIntent.ShowRestoreSetAside.emit()
                }
            ).takeIf { dialogState.item.canRestore },
            AppActionItem(
                icon = Icons.Rounded.SaveAlt,
                title = stringResource(R.string.security_action_export),
                onClick = { SecuritySettingsUiIntent.ExportSetAside.emit() }
            ),
            AppActionItem(
                icon = Icons.Rounded.Delete,
                title = stringResource(R.string.security_set_aside_delete),
                isDestructive = true,
                onClick = {
                    chosen = true
                    SecuritySettingsUiIntent.ShowDeleteSetAside.emit()
                }
            )
        )
    )
}

/**
 * Recovery code of a set-aside copy. The typed code lives only in this dialog's memory (not in
 * saved instance state) and is handed to the ViewModel once, on confirm.
 */
@Composable
private fun RestoreCodeDialog(state: SecuritySettingsDialogState.RestoreCodeInput, emit: SecuritySettingsUiIntent.() -> Unit) {
    var code by remember { mutableStateOf("") }
    AppInputDialog(
        onDismissRequest = { SecuritySettingsUiIntent.DismissDialog.emit() },
        title = stringResource(R.string.security_restore_code_title),
        subtitle = stringResource(
            when (state.error) {
                RestoreCodeError.Malformed -> R.string.security_recovery_error_malformed
                RestoreCodeError.WrongCode -> R.string.security_recovery_error_wrong
                null -> R.string.security_restore_code_subtitle
            }
        ),
        value = code,
        onValueChange = { code = it },
        label = stringResource(R.string.security_recovery_dialog_label),
        placeholder = "XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX",
        password = true,
        badgeIcon = Icons.Rounded.Key,
        badgeTone = if (state.error == null) DialogBadgeTone.Primary else DialogBadgeTone.Danger,
        confirmText = stringResource(R.string.security_restore_confirm),
        enabled = !state.isWorking,
        isConfirmLoading = state.isWorking,
        onConfirm = { SecuritySettingsUiIntent.SubmitRestoreCode(code).emit() }
    )
}

/**
 * Shows a new recovery code once. The dialog window is secure (no screenshots, hidden in
 * recents), the code cannot be selected or copied, and it cannot be dismissed by tapping
 * outside, so the user confirms they wrote it down.
 */
@Composable
private fun RecoveryCodeDialog(code: String, onDone: () -> Unit) {
    AppDialogScaffold(
        onDismissRequest = {},
        title = stringResource(R.string.security_recovery_show_title),
        subtitle = stringResource(R.string.security_recovery_show_message),
        badgeIcon = Icons.Rounded.Key,
        badgeTone = DialogBadgeTone.Warning,
        confirmText = stringResource(R.string.security_recovery_show_confirm),
        dismissText = null,
        onConfirm = onDone,
        onDismiss = null,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            securePolicy = SecureFlagPolicy.SecureOn
        )
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            DisableSelection {
                Text(
                    text = code.replace("-", " - "),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = HimitsuMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

private fun LockTimeout.labelRes(): Int = when (this) {
    LockTimeout.Immediately -> R.string.security_timeout_immediately
    LockTimeout.OneMinute -> R.string.security_timeout_one_minute
    LockTimeout.FiveMinutes -> R.string.security_timeout_five_minutes
}

@HimitsuPhonePreviews
@Composable
private fun SecuritySettingsLayoutPreview() {
    AppTheme(dynamicColor = false) {
        SecuritySettingsLayout(
            uiState = SecuritySettingsUiState.Normal(
                appLockEnabled = true,
                deviceSecure = true,
                lockTimeout = LockTimeout.OneMinute,
                hasRecoveryCode = true,
                secureScreenEnabled = false
            ),
            emit = {}
        )
    }
}
