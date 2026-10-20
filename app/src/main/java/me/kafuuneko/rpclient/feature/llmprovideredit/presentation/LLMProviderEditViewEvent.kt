// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.llmprovideredit.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** One-off actions of the provider editor that the Activity performs. */
sealed class LLMProviderEditViewEvent : IViewEvent {
    /** Opens the system file picker to import a GGUF model. */
    data object OpenLocalModelPicker : LLMProviderEditViewEvent()
}

/** Progress of a GGUF import started from the provider editor. */
sealed class LocalModelImportState {
    data object Idle : LocalModelImportState()

    /** Copying; [percent] is null when the file size is unknown. */
    data class Copying(val percent: Int?) : LocalModelImportState()
}
