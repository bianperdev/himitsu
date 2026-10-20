// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.presentation

import me.kafuuneko.rpclient.libs.security.lock.AuthOutcome

/** User actions on the decoy frame. */
sealed class DisguiseUiIntent {
    data class Init(val versionName: String, val covering: Boolean) : DisguiseUiIntent()
    data object ShowMenu : DisguiseUiIntent()
    data object DismissMenu : DisguiseUiIntent()
    data object ShowAbout : DisguiseUiIntent()
    data object DismissAbout : DisguiseUiIntent()

    /** The version number in About was held long enough: the fixed recovery gesture. */
    data object RecoveryGesture : DisguiseUiIntent()

    /** The decoy's own shortcut matched. */
    data object ShortcutMatched : DisguiseUiIntent()

    /** The system authentication closed. */
    data class AuthenticationFinished(val outcome: AuthOutcome) : DisguiseUiIntent()
}
