// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.settings.presentation

import me.kafuuneko.rpclient.feature.disguise.converter.presentation.ConverterUiIntent
import me.kafuuneko.rpclient.libs.disguise.Appearance
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorKey
import me.kafuuneko.rpclient.libs.disguise.compass.CardinalPoint

/** User actions on the disguise settings screen. */
sealed class DisguiseSettingsUiIntent {
    data object Init : DisguiseSettingsUiIntent()
    data object Resume : DisguiseSettingsUiIntent()
    data object Back : DisguiseSettingsUiIntent()
    data object OpenSecuritySettings : DisguiseSettingsUiIntent()

    /** The disguise switch was turned on. */
    data object StartEnable : DisguiseSettingsUiIntent()

    /** Pick another decoy or a new shortcut while disguise stays on. */
    data object StartChange : DisguiseSettingsUiIntent()

    data object ShowLimits : DisguiseSettingsUiIntent()
    data class ToggleQuickExitVolumeKey(val enabled: Boolean) : DisguiseSettingsUiIntent()

    /** The disguise switch was turned off. */
    data object StartDisable : DisguiseSettingsUiIntent()

    data class SelectDecoy(val appearance: Appearance) : DisguiseSettingsUiIntent()
    data object ConfirmDecoy : DisguiseSettingsUiIntent()

    /** A new code and its repetition. [toString] never prints them. */
    class SubmitCode(val code: String, val repeated: String) : DisguiseSettingsUiIntent() {
        override fun toString(): String = "SubmitCode([REDACTED])"
    }

    data class PracticePress(val key: CalculatorKey) : DisguiseSettingsUiIntent()

    /** An action on the practice converter. */
    data class PracticeConverter(val intent: ConverterUiIntent) : DisguiseSettingsUiIntent()

    /** A letter of the still dial was tapped while choosing the compass sequence. */
    class TapSequence(val point: CardinalPoint) : DisguiseSettingsUiIntent() {
        override fun toString(): String = "TapSequence([REDACTED])"
    }

    data object ClearSequence : DisguiseSettingsUiIntent()
    data object NextSequence : DisguiseSettingsUiIntent()
    data object FinishPractice : DisguiseSettingsUiIntent()
    data object ConfirmChange : DisguiseSettingsUiIntent()

    /** Result of the credential confirmation asked before changing or turning off an active disguise. */
    data class CredentialConfirmed(val confirmed: Boolean) : DisguiseSettingsUiIntent()
    data object Cancel : DisguiseSettingsUiIntent()
}
