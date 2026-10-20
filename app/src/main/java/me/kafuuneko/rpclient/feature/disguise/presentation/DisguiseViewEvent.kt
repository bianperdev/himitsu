// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** One-time actions the decoy host must perform. */
sealed class DisguiseViewEvent : IViewEvent {
    /** Show the system authentication over the decoy. */
    data object ShowPrompt : DisguiseViewEvent()

    /** Open the app gate screen (storage failure, or a device without a screen lock). */
    data object OpenGateScreen : DisguiseViewEvent()

    /** Close this covering decoy to reveal the Himitsu page under it. */
    data object RevealPage : DisguiseViewEvent()

    /** Open Himitsu's main screen above the decoy. */
    data object OpenHimitsu : DisguiseViewEvent()
}
