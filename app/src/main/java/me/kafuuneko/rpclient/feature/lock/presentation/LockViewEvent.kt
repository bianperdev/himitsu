// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** One-time actions the gate screen host must perform. */
sealed class LockViewEvent : IViewEvent {
    /** Show the system authentication prompt. */
    data object ShowAuthenticationPrompt : LockViewEvent()
}
