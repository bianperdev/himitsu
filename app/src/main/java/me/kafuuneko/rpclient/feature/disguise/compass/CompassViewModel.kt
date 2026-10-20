// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.compass

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.feature.disguise.compass.presentation.CompassUiIntent
import me.kafuuneko.rpclient.feature.disguise.compass.presentation.CompassUiState
import me.kafuuneko.rpclient.feature.disguise.compass.presentation.CompassViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.disguise.Appearance
import me.kafuuneko.rpclient.libs.disguise.compass.CardinalPoint
import me.kafuuneko.rpclient.libs.disguise.compass.CompassMath
import me.kafuuneko.rpclient.libs.disguise.compass.CompassReading
import me.kafuuneko.rpclient.libs.disguise.compass.CompassSensor
import me.kafuuneko.rpclient.libs.disguise.secret.DisguiseSecretStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Lengths a compass tap sequence may have. */
val COMPASS_SEQUENCE_LENGTHS: IntRange = 4..8

/** Quiet time after the last letter tap before the sequence is checked. */
private const val SEQUENCE_PAUSE_MILLIS = 1_500L

/**
 * ViewModel of the compass decoy.
 *
 * Responsibilities:
 * - read the sensors only while the screen is visible
 * - collect taps on the dial letters; [SEQUENCE_PAUSE_MILLIS] after the last one, check the
 *   sequence in the background. Taps never change what is shown, so a wrong sequence looks
 *   like idle tapping
 * - on a match, ask the host for authentication; it never opens content itself
 */
class CompassViewModel : CoreViewModelWithEvent<CompassUiIntent, CompassUiState>(CompassUiState.None), KoinComponent {
    private val mSensor by inject<CompassSensor>()
    private val mSecretStore by inject<DisguiseSecretStore>()
    private var mReadingJob: Job? = null
    private var mSequenceJob: Job? = null
    private val mTaps = mutableListOf<CardinalPoint>()

    @UiIntentObserver(CompassUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<CompassUiState.None>()) return
        CompassUiState.Normal(mode = mSensor.mode).setup()
    }

    @UiIntentObserver(CompassUiIntent.Resume::class)
    private fun onResume(intent: CompassUiIntent.Resume) {
        if (!isStateOf<CompassUiState.Normal>()) return
        mReadingJob?.cancel()
        mReadingJob = viewModelScope.launch {
            mSensor.readings(intent.displayRotation).collect { reading ->
                val uiState = getOrNull<CompassUiState.Normal>() ?: return@collect
                when (reading) {
                    is CompassReading.Heading -> uiState.copy(heading = reading.degrees, needsCalibration = reading.needsCalibration)
                    is CompassReading.Level -> uiState.copy(levelX = reading.x, levelY = reading.y)
                }.setup()
            }
        }
    }

    @UiIntentObserver(CompassUiIntent.Pause::class)
    private fun onPause() {
        mReadingJob?.cancel()
        mReadingJob = null
    }

    @UiIntentObserver(CompassUiIntent.TapPoint::class)
    private fun onTapPoint(intent: CompassUiIntent.TapPoint) {
        mTaps += intent.point
        mSequenceJob?.cancel()
        mSequenceJob = viewModelScope.launch {
            delay(SEQUENCE_PAUSE_MILLIS)
            val taps = mTaps.toList()
            mTaps.clear()
            if (taps.size !in COMPASS_SEQUENCE_LENGTHS) return@launch
            val candidate = CompassMath.sequenceCode(taps).toCharArray()
            if (mSecretStore.verify(Appearance.Compass, candidate)) CompassViewEvent.ShortcutMatched.tryEmit()
        }
    }
}
