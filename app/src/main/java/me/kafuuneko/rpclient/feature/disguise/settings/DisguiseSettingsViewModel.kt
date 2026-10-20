// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.settings

import android.os.Build
import java.util.Locale
import me.kafuuneko.rpclient.feature.disguise.calculator.CALCULATOR_CODE_LENGTHS
import me.kafuuneko.rpclient.feature.disguise.calculator.calculatorUiState
import me.kafuuneko.rpclient.feature.disguise.compass.COMPASS_SEQUENCE_LENGTHS
import me.kafuuneko.rpclient.feature.disguise.converter.converterUiState
import me.kafuuneko.rpclient.feature.disguise.converter.presentation.ConverterUiIntent
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
import me.kafuuneko.rpclient.libs.disguise.compass.CardinalPoint
import me.kafuuneko.rpclient.libs.disguise.compass.CompassMath
import me.kafuuneko.rpclient.libs.disguise.converter.ConverterEngine
import me.kafuuneko.rpclient.libs.disguise.converter.ConverterState
import me.kafuuneko.rpclient.libs.disguise.secret.DisguiseSecretStore
import me.kafuuneko.rpclient.libs.llm.local.LocalGenerationKeeper
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelDownloadTracker
import me.kafuuneko.rpclient.libs.security.lock.AppLockManager
import me.kafuuneko.rpclient.libs.security.lock.SecurityPrefs
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * ViewModel of the disguise settings screen.
 *
 * Responsibilities:
 * - turn disguise on only when the app lock can protect what is behind it
 * - let the user pick a decoy and set its shortcut, then make them use the shortcut once on a
 *   working copy of the decoy before anything changes, so nobody applies a shortcut they cannot
 *   reproduce
 * - store the shortcut as a hash, switch the launcher icon and close the app; turning disguise
 *   off restores the Himitsu icon and deletes the shortcut
 *
 * The shortcut being set up lives only in the private fields below, never in UI state, and is
 * wiped as soon as it is stored or the setup is canceled. No step touches the user's data.
 */
class DisguiseSettingsViewModel :
    CoreViewModelWithEvent<DisguiseSettingsUiIntent, DisguiseSettingsUiState>(DisguiseSettingsUiState.None),
    KoinComponent {
    private val mAppearanceSwitcher by inject<AppearanceSwitcher>()
    private val mSecretStore by inject<DisguiseSecretStore>()
    private val mLockManager by inject<AppLockManager>()
    private val mDownloadTracker by inject<LocalModelDownloadTracker>()
    private val mFormat = CalculatorFormat(Locale.getDefault())

    /** The numeric code typed twice, for the calculator and the converter. */
    private var mPendingCode: CharArray? = null

    /** The complete shortcut to store once the practice succeeded. */
    private var mPendingSecret: CharArray? = null

    /** Compass sequence: the first entry, and the taps of the current entry. */
    private var mFirstSequence: List<CardinalPoint>? = null
    private val mSequenceTaps = mutableListOf<CardinalPoint>()

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
        uiState.copy(step = if (isBackgroundWorkRunning()) DisguiseSetupStep.Busy else DisguiseSetupStep.ChooseDecoy()).setup()
    }

    /** Sets up another decoy or a new shortcut; the current one keeps working until the new one is applied. */
    @UiIntentObserver(DisguiseSettingsUiIntent.StartChange::class)
    private fun onStartChange() {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        if (!uiState.canEnable || !uiState.appearance.isDecoy || uiState.isWorking) return
        val step = if (isBackgroundWorkRunning()) DisguiseSetupStep.Busy else DisguiseSetupStep.ChooseDecoy(uiState.appearance)
        uiState.copy(step = step).setup()
    }

    @UiIntentObserver(DisguiseSettingsUiIntent.StartDisable::class)
    private fun onStartDisable() {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        if (!uiState.appearance.isDecoy || uiState.isWorking) return
        val step = if (isBackgroundWorkRunning()) DisguiseSetupStep.Busy else DisguiseSetupStep.ConfirmChange(Appearance.Himitsu)
        uiState.copy(step = step).setup()
    }

    @UiIntentObserver(DisguiseSettingsUiIntent.ShowLimits::class)
    private fun onShowLimits() {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        if (uiState.step != DisguiseSetupStep.None) return
        uiState.copy(step = DisguiseSetupStep.Limits).setup()
    }

    @UiIntentObserver(DisguiseSettingsUiIntent.SelectDecoy::class)
    private fun onSelectDecoy(intent: DisguiseSettingsUiIntent.SelectDecoy) {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        if (uiState.step !is DisguiseSetupStep.ChooseDecoy || !intent.appearance.isDecoy) return
        uiState.copy(step = DisguiseSetupStep.ChooseDecoy(intent.appearance)).setup()
    }

    /** The compass shortcut is a tap sequence; the others start with a numeric code. */
    @UiIntentObserver(DisguiseSettingsUiIntent.ConfirmDecoy::class)
    private fun onConfirmDecoy() {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        val step = uiState.step as? DisguiseSetupStep.ChooseDecoy ?: return
        val next = if (step.selected == Appearance.Compass) {
            resetSequence()
            DisguiseSetupStep.CompassSequence()
        } else {
            DisguiseSetupStep.EnterCode(step.selected)
        }
        uiState.copy(step = next).setup()
    }

    /** Accepts a code of the allowed length typed the same way twice, then starts the practice. */
    @UiIntentObserver(DisguiseSettingsUiIntent.SubmitCode::class)
    private fun onSubmitCode(intent: DisguiseSettingsUiIntent.SubmitCode) {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        val step = uiState.step as? DisguiseSetupStep.EnterCode ?: return
        val error = when {
            intent.code.length !in CALCULATOR_CODE_LENGTHS || !intent.code.all { it in '0'..'9' } -> DisguiseCodeError.Length
            intent.code != intent.repeated -> DisguiseCodeError.Mismatch
            else -> null
        }
        if (error != null) {
            uiState.copy(step = step.copy(error = error)).setup()
            return
        }
        wipePending()
        mPendingCode = intent.code.toCharArray()
        val practice = if (step.target == Appearance.Converter) {
            DisguiseSetupStep.PracticeConverter(converterUiState(ConverterState(), mFormat))
        } else {
            DisguiseSetupStep.PracticeCalculator(calculatorUiState(CalculatorState(), mFormat))
        }
        uiState.copy(step = practice).setup()
    }

    /** Runs the practice calculator; typing the pending code and "=" marks the practice as done. */
    @UiIntentObserver(DisguiseSettingsUiIntent.PracticePress::class)
    private fun onPracticePress(intent: DisguiseSettingsUiIntent.PracticePress) {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        val step = uiState.step as? DisguiseSetupStep.PracticeCalculator ?: return
        val engine = step.calculator.engine
        val candidate = if (intent.key == CalculatorKey.Equals) CalculatorEngine.shortcutCandidate(engine, CALCULATOR_CODE_LENGTHS) else null
        val matched = candidate != null && mPendingCode?.contentEquals(candidate.toCharArray()) == true
        if (matched) replacePendingSecret(candidate!!.toCharArray())
        val calculator = calculatorUiState(CalculatorEngine.press(engine, intent.key), mFormat)
        uiState.copy(step = DisguiseSetupStep.PracticeCalculator(calculator, step.matched || matched)).setup()
    }

    /**
     * Runs the practice converter. Swapping with the pending code typed records the units on
     * screen as part of the shortcut; a later successful swap replaces them.
     */
    @UiIntentObserver(DisguiseSettingsUiIntent.PracticeConverter::class)
    private fun onPracticeConverter(intent: DisguiseSettingsUiIntent.PracticeConverter) {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        val step = uiState.step as? DisguiseSetupStep.PracticeConverter ?: return
        val engine = step.converter.engine
        var matched = step.matched
        val next = when (val action = intent.intent) {
            is ConverterUiIntent.Press -> ConverterEngine.press(engine, action.key)
            is ConverterUiIntent.SelectCategory -> ConverterEngine.selectCategory(engine, action.category)
            is ConverterUiIntent.SelectFrom -> ConverterEngine.selectFrom(engine, action.unit)
            is ConverterUiIntent.SelectTo -> ConverterEngine.selectTo(engine, action.unit)
            ConverterUiIntent.Swap -> {
                if (mPendingCode?.contentEquals(engine.input.toCharArray()) == true) {
                    replacePendingSecret(ConverterEngine.shortcutText(engine.category, engine.from, engine.to, engine.input).toCharArray())
                    matched = true
                }
                ConverterEngine.swap(engine)
            }
            ConverterUiIntent.Init -> engine
        }
        uiState.copy(step = DisguiseSetupStep.PracticeConverter(converterUiState(next, mFormat), matched)).setup()
    }

    @UiIntentObserver(DisguiseSettingsUiIntent.TapSequence::class)
    private fun onTapSequence(intent: DisguiseSettingsUiIntent.TapSequence) {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        val step = uiState.step as? DisguiseSetupStep.CompassSequence ?: return
        if (mSequenceTaps.size >= COMPASS_SEQUENCE_LENGTHS.last) return
        mSequenceTaps += intent.point
        uiState.copy(step = step.copy(taps = mSequenceTaps.size, error = null)).setup()
    }

    @UiIntentObserver(DisguiseSettingsUiIntent.ClearSequence::class)
    private fun onClearSequence() {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        val step = uiState.step as? DisguiseSetupStep.CompassSequence ?: return
        mSequenceTaps.clear()
        uiState.copy(step = step.copy(taps = 0, error = null)).setup()
    }

    /** Stores the first sequence, then checks the repetition; a mismatch starts over. */
    @UiIntentObserver(DisguiseSettingsUiIntent.NextSequence::class)
    private fun onNextSequence() {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        val step = uiState.step as? DisguiseSetupStep.CompassSequence ?: return
        val taps = mSequenceTaps.toList()
        mSequenceTaps.clear()
        val first = mFirstSequence
        val next = when {
            taps.size !in COMPASS_SEQUENCE_LENGTHS -> step.copy(taps = 0, error = DisguiseCodeError.Length)
            first == null -> {
                mFirstSequence = taps
                DisguiseSetupStep.CompassSequence(repeating = true)
            }
            first == taps -> {
                replacePendingSecret(CompassMath.sequenceCode(taps).toCharArray())
                mFirstSequence = null
                DisguiseSetupStep.ConfirmChange(Appearance.Compass)
            }
            else -> {
                mFirstSequence = null
                DisguiseSetupStep.CompassSequence(error = DisguiseCodeError.Mismatch)
            }
        }
        uiState.copy(step = next).setup()
    }

    @UiIntentObserver(DisguiseSettingsUiIntent.FinishPractice::class)
    private fun onFinishPractice() {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        val target = when (val step = uiState.step) {
            is DisguiseSetupStep.PracticeCalculator -> Appearance.Calculator.takeIf { step.matched }
            is DisguiseSetupStep.PracticeConverter -> Appearance.Converter.takeIf { step.matched }
            else -> null
        } ?: return
        uiState.copy(step = DisguiseSetupStep.ConfirmChange(target)).setup()
    }

    /**
     * Applies the change confirmed in the warning.
     *
     * - Turning on stores the shortcut hash first, then switches the icon, so the decoy never
     *   shows up without a working shortcut.
     * - Turning off restores the icon first, then deletes the shortcut.
     */
    @UiIntentObserver(DisguiseSettingsUiIntent.ConfirmChange::class)
    private suspend fun onConfirmChange() {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        val step = uiState.step as? DisguiseSetupStep.ConfirmChange ?: return
        if (uiState.isWorking) return
        if (isBackgroundWorkRunning()) {
            uiState.copy(step = DisguiseSetupStep.Busy).setup()
            return
        }
        uiState.copy(isWorking = true).setup()
        if (step.target.isDecoy) {
            val secret = mPendingSecret ?: return onCancel()
            mPendingSecret = null
            mSecretStore.set(step.target, secret)
            wipePending()
            mAppearanceSwitcher.apply(step.target)
        } else {
            mAppearanceSwitcher.apply(Appearance.Himitsu)
            mSecretStore.clear()
        }
        uiState.copy(appearance = mAppearanceSwitcher.current, step = DisguiseSetupStep.None, isWorking = false).setup()
        DisguiseSettingsViewEvent.CloseApp.tryEmit()
    }

    @UiIntentObserver(DisguiseSettingsUiIntent.Cancel::class)
    private fun onCancel() {
        val uiState = getOrNull<DisguiseSettingsUiState.Normal>() ?: return
        wipePending()
        uiState.copy(step = DisguiseSetupStep.None, isWorking = false).setup()
    }

    override fun onCleared() {
        super.onCleared()
        wipePending()
    }

    /**
     * Switching the launcher alias may restart the process on some devices, which would stop a
     * model download or a local reply.
     */
    private fun isBackgroundWorkRunning(): Boolean = mDownloadTracker.isBusy || LocalGenerationKeeper.isGenerating

    /** Android 13 and later hide the preview with the app lock alone; earlier versions need `FLAG_SECURE`. */
    private fun isRecentsPreviewVisible(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && !SecurityPrefs.secureScreenEnabled

    private fun replacePendingSecret(secret: CharArray) {
        mPendingSecret?.fill('\u0000')
        mPendingSecret = secret
    }

    private fun resetSequence() {
        mFirstSequence = null
        mSequenceTaps.clear()
    }

    private fun wipePending() {
        mPendingCode?.fill('\u0000')
        mPendingCode = null
        mPendingSecret?.fill('\u0000')
        mPendingSecret = null
        resetSequence()
    }
}
