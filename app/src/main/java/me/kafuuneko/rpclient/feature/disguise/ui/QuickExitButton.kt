// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.disguise.QuickExit
import me.kafuuneko.rpclient.libs.disguise.isDisguiseActive

/** Button that shows the decoy at once; drawn only while disguise is on. */
@Composable
fun QuickExitButton(modifier: Modifier = Modifier) {
    val active = remember { isDisguiseActive() }
    if (!active) return
    val activity = LocalActivity.current ?: return
    FilledTonalIconButton(onClick = { QuickExit.open(activity) }, modifier = modifier) {
        Icon(Icons.Rounded.VisibilityOff, contentDescription = stringResource(R.string.disguise_quick_exit))
    }
}
