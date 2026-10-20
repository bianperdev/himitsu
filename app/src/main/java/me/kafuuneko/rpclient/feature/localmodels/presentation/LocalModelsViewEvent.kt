// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.localmodels.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** One-off actions the Activity performs. */
sealed class LocalModelsViewEvent : IViewEvent {
    /** Opens the system file picker for a GGUF file. */
    data object OpenModelPicker : LocalModelsViewEvent()

    /** Starts the download service, asking for the notification permission first when needed. */
    data class StartDownload(val key: String) : LocalModelsViewEvent()
}
