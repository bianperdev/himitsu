// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.compass.presentation

import me.kafuuneko.rpclient.libs.disguise.compass.CompassMode

/** State of the compass decoy. */
sealed class CompassUiState {
    data object None : CompassUiState()

    /**
     * @property heading magnetic heading in degrees, null until the first reading
     * @property levelX tilt for the bubble level when there is no magnetometer, between -1 and 1
     */
    data class Normal(
        val mode: CompassMode,
        val heading: Float? = null,
        val needsCalibration: Boolean = false,
        val levelX: Float = 0f,
        val levelY: Float = 0f
    ) : CompassUiState()
}
