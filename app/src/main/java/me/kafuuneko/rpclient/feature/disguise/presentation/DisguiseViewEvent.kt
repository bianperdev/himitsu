// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** One-time actions the decoy host must perform. */
sealed class DisguiseViewEvent : IViewEvent {
    /** The shortcut or the recovery gesture was used: show the system authentication. */
    data object RequestAuthentication : DisguiseViewEvent()
}
