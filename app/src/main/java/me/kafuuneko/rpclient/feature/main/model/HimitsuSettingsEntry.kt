// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.main.model

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.ui.graphics.vector.ImageVector
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.localmodels.LocalModelsActivity

/**
 * A settings entry contributed by a Himitsu work track.
 *
 * The entry is shown on the main settings page and opens the track's own settings screen,
 * which has its own ViewModel. Tracks never edit `MainViewModel` or `MainSettingsState`.
 */
data class HimitsuSettingsEntry(
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
    val icon: ImageVector,
    val activity: Class<out Activity>
)

/** Settings entries contributed by Himitsu tracks, in display order. Each track appends one entry. */
internal val himitsuSettingsEntries: List<HimitsuSettingsEntry> = listOf(
    HimitsuSettingsEntry(
        titleRes = R.string.local_models_title,
        subtitleRes = R.string.local_models_settings_desc,
        icon = Icons.Rounded.Memory,
        activity = LocalModelsActivity::class.java
    ), // feat/local-inference
)
