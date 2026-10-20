// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.compass.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** One-time actions of the compass decoy. */
sealed class CompassViewEvent : IViewEvent {
    /** The disguise sequence was tapped: the host asks for authentication. */
    data object ShortcutMatched : CompassViewEvent()
}
