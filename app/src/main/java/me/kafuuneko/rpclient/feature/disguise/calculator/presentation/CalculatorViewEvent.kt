// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.calculator.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** One-time actions of the calculator decoy. */
sealed class CalculatorViewEvent : IViewEvent {
    /** The disguise code was entered: the host asks for authentication. */
    data object ShortcutMatched : CalculatorViewEvent()
}
