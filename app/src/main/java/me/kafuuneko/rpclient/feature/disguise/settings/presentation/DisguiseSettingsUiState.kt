// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.settings.presentation

import me.kafuuneko.rpclient.feature.disguise.calculator.presentation.CalculatorUiState
import me.kafuuneko.rpclient.feature.disguise.converter.presentation.ConverterUiState
import me.kafuuneko.rpclient.libs.disguise.Appearance

/** State of the disguise settings screen. */
sealed class DisguiseSettingsUiState {
    data object None : DisguiseSettingsUiState()

    /**
     * @property appearance what the launcher shows now; a decoy means disguise is on
     * @property appLockEnabled disguise needs the app lock to keep content behind authentication
     * @property deviceSecure the app lock needs a device screen lock
     * @property recentsPreviewVisible before Android 13 only "hide content" keeps Himitsu out of
     *   the recent apps preview
     */
    data class Normal(
        val appearance: Appearance,
        val appLockEnabled: Boolean,
        val deviceSecure: Boolean,
        val recentsPreviewVisible: Boolean = false,
        val step: DisguiseSetupStep = DisguiseSetupStep.None,
        val isWorking: Boolean = false
    ) : DisguiseSettingsUiState() {
        /** Whether disguise can be turned on right now. */
        val canEnable: Boolean
            get() = appLockEnabled && deviceSecure
    }

    data class Finished(val previous: DisguiseSettingsUiState) : DisguiseSettingsUiState()
}

/** Why a new code or sequence was refused. */
enum class DisguiseCodeError { Length, Mismatch }

/**
 * Steps of turning disguise on or off. The shortcut being set up is never kept in UI state;
 * practice states redact what is typed in `toString`.
 */
sealed class DisguiseSetupStep {
    data object None : DisguiseSetupStep()

    /** The user picks the decoy. */
    data class ChooseDecoy(val selected: Appearance = Appearance.Calculator) : DisguiseSetupStep()

    /** The user chooses a numeric code (calculator and converter). */
    data class EnterCode(val target: Appearance, val error: DisguiseCodeError? = null) : DisguiseSetupStep()

    /**
     * The user tries the code on a working calculator before anything changes.
     *
     * @property matched the code was typed and "=" pressed, so it may be applied
     */
    data class PracticeCalculator(val calculator: CalculatorUiState.Normal, val matched: Boolean = false) : DisguiseSetupStep()

    /**
     * The user picks the two units, types the code and presses swap; the units become part of
     * the shortcut.
     */
    data class PracticeConverter(val converter: ConverterUiState.Normal, val matched: Boolean = false) : DisguiseSetupStep()

    /**
     * The user taps the compass sequence on a still dial, then repeats it.
     *
     * @property taps how many letters are tapped so far, never which ones
     */
    data class CompassSequence(val taps: Int = 0, val repeating: Boolean = false, val error: DisguiseCodeError? = null) :
        DisguiseSetupStep()

    /** The last warning before the launcher icon changes. */
    data class ConfirmChange(val target: Appearance) : DisguiseSetupStep()
}
