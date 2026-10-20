// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.presentation

import me.kafuuneko.rpclient.libs.disguise.Appearance

/** State of the decoy frame shared by every disguise: its menu and its About dialog. */
sealed class DisguiseUiState {
    data object None : DisguiseUiState()

    /**
     * @property appearance the decoy on screen
     * @property versionName shown in About, where the recovery gesture lives
     * @property covering the decoy hides a Himitsu page because the app locked
     */
    data class Normal(
        val appearance: Appearance,
        val versionName: String,
        val covering: Boolean,
        val menuExpanded: Boolean = false,
        val aboutVisible: Boolean = false
    ) : DisguiseUiState()
}
