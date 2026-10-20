// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.disguise.ui.AppearanceIcon
import me.kafuuneko.rpclient.libs.disguise.Appearance
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.HimitsuPhonePreviews

/**
 * Warning shown before the launcher icon changes, from [previous] to [target].
 *
 * It says what the user will see in the launcher, that the app may close, and, for a decoy,
 * how to get in and how to recover if the shortcut is forgotten.
 */
@Composable
fun AppearanceChangeDialog(
    target: Appearance,
    previous: Appearance,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = if (target.isDecoy) {
        stringResource(R.string.disguise_change_title, stringResource(target.labelRes))
    } else {
        stringResource(R.string.disguise_restore_title)
    }
    AppDialogScaffold(
        onDismissRequest = onDismiss,
        title = title,
        confirmText = stringResource(R.string.disguise_change_confirm),
        onConfirm = onConfirm
    ) {
        AppearanceIcon(target)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (target.isDecoy) {
                Note(stringResource(R.string.disguise_change_home_icon, stringResource(target.labelRes)))
            } else {
                Note(stringResource(R.string.disguise_restore_home_icon, stringResource(previous.labelRes)))
            }
            Note(stringResource(R.string.disguise_change_app_closes))
            entryHintRes(target)?.let {
                Note(stringResource(it))
                Note(stringResource(R.string.disguise_change_recovery))
            }
            Note(stringResource(R.string.disguise_change_two_icons))
        }
    }
}

/** One bullet of the warning, with a hanging indent. */
@Composable
private fun Note(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
    }
}

/** How to reach authentication from [appearance], or null for Himitsu itself. */
@StringRes
private fun entryHintRes(appearance: Appearance): Int? = when (appearance) {
    Appearance.Himitsu -> null
    Appearance.Calculator -> R.string.disguise_change_entry_calculator
    Appearance.Compass -> R.string.disguise_change_entry_compass
    Appearance.Converter -> R.string.disguise_change_entry_converter
}

@HimitsuPhonePreviews
@Composable
private fun AppearanceChangeDialogPreview() {
    AppTheme(dynamicColor = false) {
        AppearanceChangeDialog(target = Appearance.Calculator, previous = Appearance.Himitsu, onConfirm = {}, onDismiss = {})
    }
}

@HimitsuPhonePreviews
@Composable
private fun AppearanceRestoreDialogPreview() {
    AppTheme(dynamicColor = false) {
        AppearanceChangeDialog(target = Appearance.Himitsu, previous = Appearance.Compass, onConfirm = {}, onDismiss = {})
    }
}
