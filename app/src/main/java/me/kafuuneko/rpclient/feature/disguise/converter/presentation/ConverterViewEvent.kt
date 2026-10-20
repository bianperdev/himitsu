// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.converter.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** One-time actions of the unit converter decoy. */
sealed class ConverterViewEvent : IViewEvent {
    /** The disguise value and units were entered: the host asks for authentication. */
    data object ShortcutMatched : ConverterViewEvent()
}
