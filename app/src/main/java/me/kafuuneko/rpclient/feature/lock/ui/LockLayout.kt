// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.lock.presentation.LockContentState
import me.kafuuneko.rpclient.feature.lock.presentation.LockDialogState
import me.kafuuneko.rpclient.feature.lock.presentation.LockUiIntent
import me.kafuuneko.rpclient.feature.lock.presentation.LockUiState
import me.kafuuneko.rpclient.feature.lock.presentation.RecoveryCodeError
import me.kafuuneko.rpclient.libs.security.StorageFailure
import me.kafuuneko.rpclient.ui.dialog.AppConfirmDialog
import me.kafuuneko.rpclient.ui.dialog.AppInputDialog
import me.kafuuneko.rpclient.ui.dialog.DialogBadgeTone
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.HimitsuPhonePreviews
import me.kafuuneko.rpclient.ui.widgets.RpLockMark
import me.kafuuneko.rpclient.ui.widgets.lockRoomLight

/** Entry of the gate screen. */
@Composable
fun LockLayout(
    uiState: LockUiState,
    emit: LockUiIntent.() -> Unit
) {
    when (uiState) {
        LockUiState.None -> LockScreenFrame(icon = Icons.Rounded.Lock, title = null, message = null) {}
        is LockUiState.Normal -> {
            LockContent(uiState.contentState, emit)
            LockDialogs(uiState.dialogState, emit)
        }
        is LockUiState.Finished -> LockLayout(uiState.previous) { }
    }
}

/**
 * Visual frame shared by every gate state: an icon, a title, a message and actions.
 *
 * This is the styling contract for `feat/aesthetic`: it reads only `MaterialTheme` tokens, so
 * a new theme restyles it without touching the gate logic. Text is optional so the loading
 * state shows no wording at all.
 */
@Composable
fun LockScreenFrame(
    icon: ImageVector,
    title: String?,
    message: String?,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .lockRoomLight()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RpLockMark(icon = icon)
                if (title != null) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                }
                if (message != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(28.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = actions
                )
            }
        }
    }
}

@Composable
private fun LockContent(state: LockContentState, emit: LockUiIntent.() -> Unit) {
    when (state) {
        LockContentState.Loading -> LockScreenFrame(icon = Icons.Rounded.Lock, title = null, message = null) {
            CircularProgressIndicator()
        }
        is LockContentState.StorageError -> StorageErrorContent(state, emit)
        is LockContentState.Locked -> LockedContent(state, emit)
    }
}

@Composable
private fun LockedContent(state: LockContentState.Locked, emit: LockUiIntent.() -> Unit) {
    if (!state.deviceSecure) {
        // Authentication is impossible without a device credential; say so honestly
        LockScreenFrame(
            icon = Icons.Rounded.WarningAmber,
            title = stringResource(R.string.security_lock_no_device_lock_title),
            message = stringResource(R.string.security_lock_no_device_lock_message)
        ) {
            Button(onClick = { LockUiIntent.ContinueWithoutDeviceLock.emit() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.security_lock_continue_unprotected))
            }
        }
        return
    }
    LockScreenFrame(
        icon = Icons.Rounded.Lock,
        title = stringResource(R.string.security_lock_title),
        message = state.errorMessage
    ) {
        Button(onClick = { LockUiIntent.Unlock.emit() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.security_lock_unlock))
        }
    }
}

@Composable
private fun StorageErrorContent(state: LockContentState.StorageError, emit: LockUiIntent.() -> Unit) {
    val message = stringResource(state.failure.messageRes()) + "\n\n" +
        stringResource(R.string.security_storage_error_nothing_deleted)
    LockScreenFrame(
        icon = Icons.Rounded.WarningAmber,
        title = stringResource(R.string.security_storage_error_title),
        message = message
    ) {
        if (state.canRetry) {
            Button(onClick = { LockUiIntent.Retry.emit() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.security_action_retry))
            }
        }
        if (state.canUseRecoveryCode) {
            val recoveryButton: @Composable () -> Unit = {
                Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.security_action_use_recovery_code))
            }
            if (state.canRetry) {
                OutlinedButton(onClick = { LockUiIntent.ShowRecoveryCodeDialog.emit() }, modifier = Modifier.fillMaxWidth()) {
                    recoveryButton()
                }
            } else {
                Button(onClick = { LockUiIntent.ShowRecoveryCodeDialog.emit() }, modifier = Modifier.fillMaxWidth()) {
                    recoveryButton()
                }
            }
        }
        if (state.canSetAside) {
            OutlinedButton(onClick = { LockUiIntent.ShowSetAsideDialog.emit() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.security_action_set_aside))
            }
        }
        TextButton(
            onClick = { LockUiIntent.ShowExportDialog.emit() },
            enabled = !state.isExporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(if (state.isExporting) R.string.security_export_working else R.string.security_action_export))
        }
        if (state.canStartOver) {
            TextButton(onClick = { LockUiIntent.ShowStartOverDialog.emit() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.security_action_start_over))
            }
        }
    }
}

@Composable
private fun LockDialogs(state: LockDialogState, emit: LockUiIntent.() -> Unit) {
    when (state) {
        LockDialogState.None -> Unit
        is LockDialogState.RecoveryCodeInput -> RecoveryCodeDialog(state, emit)
        LockDialogState.ConfirmStartOver -> AppConfirmDialog(
            onDismissRequest = { LockUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.security_start_over_title),
            message = stringResource(R.string.security_start_over_message),
            badgeIcon = Icons.Rounded.RestartAlt,
            badgeTone = DialogBadgeTone.Danger,
            confirmText = stringResource(R.string.security_start_over_confirm),
            confirmIsDestructive = true,
            onConfirm = { LockUiIntent.ConfirmStartOver.emit() }
        )
        LockDialogState.ConfirmSetAside -> AppConfirmDialog(
            onDismissRequest = { LockUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.security_set_aside_title),
            message = stringResource(R.string.security_set_aside_message),
            badgeIcon = Icons.Rounded.WarningAmber,
            badgeTone = DialogBadgeTone.Danger,
            confirmText = stringResource(R.string.security_set_aside_confirm),
            confirmIsDestructive = true,
            onConfirm = { LockUiIntent.ConfirmSetAside.emit() }
        )
        LockDialogState.ConfirmExport -> AppConfirmDialog(
            onDismissRequest = { LockUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.security_export_title),
            message = stringResource(R.string.security_export_message),
            badgeIcon = Icons.Rounded.Key,
            badgeTone = DialogBadgeTone.Primary,
            confirmText = stringResource(R.string.security_export_confirm),
            onConfirm = { LockUiIntent.ConfirmExport.emit() }
        )
    }
}

/**
 * Recovery code input. The typed code lives only in this dialog's memory (not in saved
 * instance state) and is handed to the ViewModel once, on confirm.
 */
@Composable
private fun RecoveryCodeDialog(state: LockDialogState.RecoveryCodeInput, emit: LockUiIntent.() -> Unit) {
    var code by remember { mutableStateOf("") }
    AppInputDialog(
        onDismissRequest = { LockUiIntent.DismissDialog.emit() },
        title = stringResource(R.string.security_recovery_dialog_title),
        subtitle = state.error?.let { stringResource(it.messageRes()) }
            ?: stringResource(R.string.security_recovery_dialog_subtitle),
        value = code,
        onValueChange = { code = it },
        label = stringResource(R.string.security_recovery_dialog_label),
        placeholder = "XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX",
        password = true,
        badgeIcon = Icons.Rounded.Key,
        badgeTone = if (state.error == null) DialogBadgeTone.Primary else DialogBadgeTone.Danger,
        confirmText = stringResource(R.string.security_recovery_dialog_confirm),
        enabled = !state.isWorking,
        isConfirmLoading = state.isWorking,
        onConfirm = { LockUiIntent.SubmitRecoveryCode(code).emit() }
    )
}

private fun StorageFailure.messageRes(): Int = when (this) {
    StorageFailure.KeyFileMissing -> R.string.security_storage_error_key_file_missing
    StorageFailure.KeyFileCorrupt -> R.string.security_storage_error_key_file_corrupt
    StorageFailure.KeystoreKeyLost -> R.string.security_storage_error_keystore_key_lost
    StorageFailure.KeystoreUnavailable -> R.string.security_storage_error_keystore_unavailable
    StorageFailure.StorageError -> R.string.security_storage_error_storage
    StorageFailure.MigrationFailed -> R.string.security_storage_error_migration_failed
    StorageFailure.PlainDatabaseUnusable -> R.string.security_storage_error_plain_unusable
    StorageFailure.RestoreIncomplete -> R.string.security_storage_error_restore_incomplete
}

private fun RecoveryCodeError.messageRes(): Int = when (this) {
    RecoveryCodeError.Malformed -> R.string.security_recovery_error_malformed
    RecoveryCodeError.WrongCode -> R.string.security_recovery_error_wrong
    RecoveryCodeError.NotConfigured -> R.string.security_recovery_error_not_configured
    RecoveryCodeError.Failed -> R.string.security_recovery_error_failed
}

@HimitsuPhonePreviews
@Composable
private fun LockLayoutStorageErrorPreview() {
    AppTheme(dynamicColor = false) {
        LockLayout(
            uiState = LockUiState.Normal(
                LockContentState.StorageError(
                    failure = StorageFailure.KeystoreKeyLost,
                    canRetry = false,
                    canUseRecoveryCode = true,
                    canStartOver = true
                )
            ),
            emit = {}
        )
    }
}
