// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.main.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.feature.main.model.HimitsuSettingsEntry
import me.kafuuneko.rpclient.feature.main.model.himitsuSettingsEntries
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsTile

/** Renders the settings entries contributed by Himitsu tracks; renders nothing when there are none. */
internal fun LazyListScope.himitsuSettingsEntryItems(
    emit: MainUiIntent.() -> Unit,
    entries: List<HimitsuSettingsEntry> = himitsuSettingsEntries
) {
    items(entries.size) { index ->
        val entry = entries[index]
        RpSettingsGroup {
            RpSettingsTile(
                icon = entry.icon,
                title = stringResource(entry.titleRes),
                subtitle = stringResource(entry.subtitleRes),
                onClick = { MainUiIntent.OpenHimitsuSettings(entry).emit() },
                trailing = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}
