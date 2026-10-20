// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.settings

import android.os.Build
import java.util.Locale
import me.kafuuneko.rpclient.feature.disguise.calculator.CALCULATOR_CODE_LENGTHS
import me.kafuuneko.rpclient.feature.disguise.calculator.calculatorUiState
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseCodeError
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseSettingsUiIntent
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseSettingsUiState
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseSettingsViewEvent
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseSetupStep
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.disguise.Appearance
import me.kafuuneko.rpclient.libs.disguise.AppearanceSwitcher
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorEngine
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorFormat
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorKey
import me.kafuuneko.rpclient.libs.disguise.calculator.CalculatorState
import me.kafuuneko.rpclient.libs.disguise.secret.DisguiseSecretStore
import me.kafuuneko.rpclient.libs.security.lock.AppLockManager
import me.kafuuneko.rpclient.libs.security.lock.SecurityPrefs
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * ViewModel of the disguise settings screen.
 *
 * Responsibilities:
 * - turn disguise on only when the app lock can protect what is behind it
 * - collect a new code, then make the user type it on a real calculator before anything
 *   changes, so nobody applies a code they cannot reproduce
 * - store the code as a hash, switch the launcher icon and close the app; turning disguise off
 *   restores the Himitsu icon and deletes the code
 *
 * The code being set up lives only in [mPendingCode], never in UI state, and is wiped as soon
 * as it is stored or the setup is canceled. No step touches the user's data.
 */
class DisguiseSettingsViewModel :
    CoreViewModelWithEvent<DisguiseSettingsUiIntent, DisguiseSettingsUiState>(DisguiseSettingsUiState.None),
    KoinComponent {
    private val mAppearanceSwitcher by inject<AppearanceSwitcher>()
    private val mSecretStore by inject<DisguiseSecretStore>()
    private val mLockManager by inject<AppLockManager>()
    private val mFormat = CalculatorFormat(Locale.getDefault())
    private var mPendingCode: CharArray? = null

    @UiIntentObserver(DisguiseSettingsUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<DisguiseSettingsUiState.None>()) return
        DisguiseSettingsUiState.Normal(
            appearance = mAppearanceSwitcher.current,
            appLockEnabled = SecurityPrefs.appLockEnabled,
            deviceSecure = mLockManager.isDeviceSecure(),
            recentsPreviewVisible = isRecentsPreviewVisible()
        ).setup()
    }

    /** Refreshes the lock requirements, which the user may change in Security or the system settings. */
    @UiIntentObserver(DisguiseSettingsUiIntent.Resume::class)
    private fun onResume() {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        uiState.copy(
            appLockEnabled = SecurityPrefs.appLockEnabled,
            deviceSecure = mLockManager.isDeviceSecure(),
            recentsPreviewVisible = isRecentsPreviewVisible()
        ).setup()
    }

    @UiIntentObserver(DisguiseSettingsUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        if (uiState.step != DisguiseSetupStep.None) {
            onCancel()
            return
        }
        DisguiseSettingsUiState.Finished(uiState).setup()
    }

    @UiIntentObserver(DisguiseSettingsUiIntent.OpenSecuritySettings::class)
    private fun onOpenSecuritySettings() {
        DisguiseSettingsViewEvent.OpenSecuritySettings.tryEmit()
    }

    @UiIntentObserver(DisguiseSettingsUiIntent.StartEnable::class)
    private fun onStartEnable() {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        if (!uiState.canEnable || uiState.appearance.isDecoy || uiState.isWorking) return
        uiState.copy(step = DisguiseSetupStep.EnterCode()).setup()
    }

    @UiIntentObserver(DisguiseSettingsUiIntent.StartDisable::class)
    private fun onStartDisable() {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        if (!uiState.appearance.isDecoy || uiState.isWorking) return
        uiState.copy(step = DisguiseSetupStep.ConfirmChange(Appearance.Himitsu)).setup()
    }

    /** Accepts a code of the allowed length typed the same way twice, then starts the practice. */
    @UiIntentObserver(DisguiseSettingsUiIntent.SubmitCode::class)
    private fun onSubmitCode(intent: DisguiseSettingsUiIntent.SubmitCode) {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        if (uiState.step !is DisguiseSetupStep.EnterCode) return
        val error = when {
            intent.code.length !in CALCULATOR_CODE_LENGTHS || !intent.code.all { it in '0'..'9' } -> DisguiseCodeError.Length
            intent.code != intent.repeated -> DisguiseCodeError.Mismatch
            else -> null
        }
        if (error != null) {
            uiState.copy(step = DisguiseSetupStep.EnterCode(error)).setup()
            return
        }
        wipePendingCode()
        mPendingCode = intent.code.toCharArray()
        uiState.copy(step = DisguiseSetupStep.Practice(calculatorUiState(CalculatorState(), mFormat))).setup()
    }

    /** Runs the practice calculator; typing the pending code and "=" marks the practice as done. */
    @UiIntentObserver(DisguiseSettingsUiIntent.PracticePress::class)
    private fun onPracticePress(intent: DisguiseSettingsUiIntent.PracticePress) {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        val step = uiState.step as? DisguiseSetupStep.Practice ?: return
        val engine = step.calculator.engine
        val candidate = if (intent.key == CalculatorKey.Equals) CalculatorEngine.shortcutCandidate(engine, CALCULATOR_CODE_LENGTHS) else null
        val matched = step.matched || (candidate != null && mPendingCode?.contentEquals(candidate.toCharArray()) == true)
        val calculator = calculatorUiState(CalculatorEngine.press(engine, intent.key), mFormat)
        uiState.copy(step = DisguiseSetupStep.Practice(calculator, matched)).setup()
    }

    @UiIntentObserver(DisguiseSettingsUiIntent.FinishPractice::class)
    private fun onFinishPractice() {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        val step = uiState.step as? DisguiseSetupStep.Practice ?: return
        if (!step.matched) return
        uiState.copy(step = DisguiseSetupStep.ConfirmChange(Appearance.Calculator)).setup()
    }

    /**
     * Applies the change confirmed in the warning.
     *
     * - Turning on stores the code hash first, then switches the icon, so the decoy never shows
     *   up without a working code.
     * - Turning off restores the icon first, then deletes the code.
     */
    @UiIntentObserver(DisguiseSettingsUiIntent.ConfirmChange::class)
    private suspend fun onConfirmChange() {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        val step = uiState.step as? DisguiseSetupStep.ConfirmChange ?: return
        if (uiState.isWorking) return
        uiState.copy(isWorking = true).setup()
        if (step.target.isDecoy) {
            val code = mPendingCode ?: return onCancel()
            mPendingCode = null
            mSecretStore.set(step.target, code)
            mAppearanceSwitcher.apply(step.target)
        } else {
            mAppearanceSwitcher.apply(Appearance.Himitsu)
            mSecretStore.clear()
        }
        DisguiseSettingsUiState.Normal(
            appearance = mAppearanceSwitcher.current,
            appLockEnabled = uiState.appLockEnabled,
            deviceSecure = uiState.deviceSecure,
            recentsPreviewVisible = uiState.recentsPreviewVisible
        ).setup()
        DisguiseSettingsViewEvent.CloseApp.tryEmit()
    }

    @UiIntentObserver(DisguiseSettingsUiIntent.Cancel::class)
    private fun onCancel() {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        wipePendingCode()
        uiState.copy(step = DisguiseSetupStep.None, isWorking = false).setup()
    }

    override fun onCleared() {
        super.onCleared()
        wipePendingCode()
    }

    /** Android 13 and later hide the preview with the app lock alone; earlier versions need `FLAG_SECURE`. */
    private fun isRecentsPreviewVisible(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && !SecurityPrefs.secureScreenEnabled

    private fun wipePendingCode() {
        mPendingCode?.fill('\u0000')
        mPendingCode = null
    }
}
