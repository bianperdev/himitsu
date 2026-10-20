// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.settings.presentation

import me.kafuuneko.rpclient.feature.disguise.calculator.presentation.CalculatorUiState
import me.kafuuneko.rpclient.libs.disguise.Appearance

/** State of the disguise settings screen. */
sealed class DisguiseSettingsUiState {
    data object None : DisguiseSettingsUiState()

    /**
     * @property appearance what the launcher shows now; a decoy means disguise is on
     * @property appLockEnabled disguise needs the app lock to keep content behind authentication
     * @property deviceSecure the app lock needs a device screen lock
     */
    data class Normal(
        val appearance: Appearance,
        val appLockEnabled: Boolean,
        val deviceSecure: Boolean,
        val step: DisguiseSetupStep = DisguiseSetupStep.None,
        val isWorking: Boolean = false
    ) : DisguiseSettingsUiState() {
        /** Whether disguise can be turned on right now. */
        val canEnable: Boolean
            get() = appLockEnabled && deviceSecure
    }

    data class Finished(val previous: DisguiseSettingsUiState) : DisguiseSettingsUiState()
}

/** Why a new code was refused. */
enum class DisguiseCodeError { Length, Mismatch }

/** Steps of turning disguise on or off. */
sealed class DisguiseSetupStep {
    data object None : DisguiseSetupStep()

    /** The user chooses the code; it is never kept in UI state. */
    data class EnterCode(val error: DisguiseCodeError? = null) : DisguiseSetupStep()

    /**
     * The user tries the code on a working calculator before anything changes.
     *
     * @property matched the code was typed and "=" pressed, so it may be applied
     */
    data class Practice(val calculator: CalculatorUiState.Normal, val matched: Boolean = false) : DisguiseSetupStep()

    /** The last warning before the launcher icon changes. */
    data class ConfirmChange(val target: Appearance) : DisguiseSetupStep()
}
