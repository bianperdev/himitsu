// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.disguise.presentation.DisguiseUiIntent
import me.kafuuneko.rpclient.feature.disguise.presentation.DisguiseUiState
import me.kafuuneko.rpclient.libs.disguise.Appearance

/** How long the version number in About must be held to start the recovery gesture. */
private const val RECOVERY_HOLD_MILLIS = 5_000L

/**
 * Frame shared by every decoy: an overflow menu with About, and the decoy below it.
 *
 * About holds the fixed recovery gesture: holding the version number for 5 seconds asks for
 * authentication, exactly like the shortcut. Nothing on screen hints at it.
 */
@Composable
fun DisguiseLayout(
    uiState: DisguiseUiState,
    emit: DisguiseUiIntent.() -> Unit,
    content: @Composable (Appearance) -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val state = uiState as? DisguiseUiState.Normal ?: return@Surface
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.fillMaxWidth().padding(4.dp)) {
                Box(Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = { DisguiseUiIntent.ShowMenu.emit() }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.disguise_menu_more))
                    }
                    DropdownMenu(expanded = state.menuExpanded, onDismissRequest = { DisguiseUiIntent.DismissMenu.emit() }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.disguise_menu_about)) },
                            onClick = { DisguiseUiIntent.ShowAbout.emit() }
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                content(state.appearance)
            }
        }
        if (state.aboutVisible) AboutDialog(state, emit)
    }
}

@Composable
private fun AboutDialog(state: DisguiseUiState.Normal, emit: DisguiseUiIntent.() -> Unit) {
    AlertDialog(
        onDismissRequest = { DisguiseUiIntent.DismissAbout.emit() },
        confirmButton = {
            TextButton(onClick = { DisguiseUiIntent.DismissAbout.emit() }) {
                Text(stringResource(R.string.disguise_about_close))
            }
        },
        icon = { AppearanceIcon(state.appearance, contentDescription = null) },
        title = { Text(stringResource(state.appearance.labelRes)) },
        text = {
            Text(
                text = stringResource(R.string.disguise_about_version, state.versionName),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .recoveryGesture { DisguiseUiIntent.RecoveryGesture.emit() }
            )
        }
    )
}

/** Calls [onRecovery] once the element has been held for [RECOVERY_HOLD_MILLIS]. */
@Composable
private fun Modifier.recoveryGesture(onRecovery: () -> Unit): Modifier {
    val currentOnRecovery by rememberUpdatedState(onRecovery)
    return pointerInput(Unit) {
        detectTapGestures(onPress = {
            val released = withTimeoutOrNull(RECOVERY_HOLD_MILLIS) { tryAwaitRelease() }
            if (released == null) currentOnRecovery()
        })
    }
}
