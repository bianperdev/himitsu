// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.presentation

/** User actions on the decoy frame. */
sealed class DisguiseUiIntent {
    data class Init(val versionName: String) : DisguiseUiIntent()
    data object ShowMenu : DisguiseUiIntent()
    data object DismissMenu : DisguiseUiIntent()
    data object ShowAbout : DisguiseUiIntent()
    data object DismissAbout : DisguiseUiIntent()

    /** The version number in About was held long enough: the fixed recovery gesture. */
    data object RecoveryGesture : DisguiseUiIntent()
}
