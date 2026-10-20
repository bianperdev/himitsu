// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.settings.presentation

import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorKey

/** User actions on the disguise settings screen. */
sealed class DisguiseSettingsUiIntent {
    data object Init : DisguiseSettingsUiIntent()
    data object Resume : DisguiseSettingsUiIntent()
    data object Back : DisguiseSettingsUiIntent()
    data object OpenSecuritySettings : DisguiseSettingsUiIntent()

    /** The disguise switch was turned on. */
    data object StartEnable : DisguiseSettingsUiIntent()

    /** The disguise switch was turned off. */
    data object StartDisable : DisguiseSettingsUiIntent()

    /** A new code and its repetition. [toString] never prints them. */
    class SubmitCode(val code: String, val repeated: String) : DisguiseSettingsUiIntent() {
        override fun toString(): String = "SubmitCode([REDACTED])"
    }

    data class PracticePress(val key: CalculatorKey) : DisguiseSettingsUiIntent()
    data object FinishPractice : DisguiseSettingsUiIntent()
    data object ConfirmChange : DisguiseSettingsUiIntent()
    data object Cancel : DisguiseSettingsUiIntent()
}
