// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.compass.presentation

import me.kafuuneko.rpclient.libs.disguise.compass.CardinalPoint

/** User actions on the compass decoy. */
sealed class CompassUiIntent {
    data object Init : CompassUiIntent()

    /** The screen is visible; sensors start. [displayRotation] is a `Surface.ROTATION_*` value. */
    data class Resume(val displayRotation: Int) : CompassUiIntent()

    /** The screen is hidden; sensors stop. */
    data object Pause : CompassUiIntent()

    /** A letter of the dial was tapped. */
    data class TapPoint(val point: CardinalPoint) : CompassUiIntent()
}
