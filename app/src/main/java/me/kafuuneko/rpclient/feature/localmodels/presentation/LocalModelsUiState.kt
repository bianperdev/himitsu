// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.localmodels.presentation

import androidx.annotation.StringRes
import me.kafuuneko.rpclient.libs.llm.local.LocalGpuStatus
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelDownloadFailure
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelMemoryFit
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelSource

/** State of the on-device models screen. */
sealed class LocalModelsUiState {
    data object None : LocalModelsUiState()

    data class Normal(
        /** Models stored on the device, newest first. */
        val models: List<LocalModelItem>,
        /** Curated downloads with their current status. */
        val catalog: List<LocalModelCatalogItem>,
        /** Copy progress of a GGUF import. */
        val importState: LocalModelsImportState = LocalModelsImportState.Idle,
        /** Device-level performance settings. */
        val performance: LocalPerformanceState = LocalPerformanceState(),
        val dialogState: LocalModelsDialogState = LocalModelsDialogState.None
    ) : LocalModelsUiState()

    data class Finished(val previous: LocalModelsUiState) : LocalModelsUiState()

    companion object {
        fun finished(previous: LocalModelsUiState): LocalModelsUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** Performance settings as the screen shows them. */
data class LocalPerformanceState(
    /** CPU threads; 0 means automatic. */
    val threads: Int = 0,
    /** What the GPU option can offer; [LocalGpuStatus.None] hides it. */
    val gpu: LocalGpuStatus = LocalGpuStatus.None,
    val useGpu: Boolean = false,
    val quantizedKvCache: Boolean = false,
    /** Seconds in the background before releasing the model; -1 keeps it loaded. */
    val unloadDelaySeconds: Int = 120
)

/** A stored model as one card of the list. */
data class LocalModelItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    /** Quantization label such as "Q4_K_M", or null when unknown. */
    val quantization: String?,
    /** Trained context length; 0 when unknown. */
    val trainedContextTokens: Int,
    val source: LocalModelSource,
    /** First characters of the SHA-256, enough to compare with a model page. */
    val sha256Prefix: String,
    val estimatedRamBytes: Long,
    val memoryFit: LocalModelMemoryFit
)

/** A curated download as one card of the catalog. */
data class LocalModelCatalogItem(
    val key: String,
    val name: String,
    @param:StringRes val descriptionRes: Int,
    val sizeBytes: Long,
    val license: String,
    val approximateRamBytes: Long,
    val memoryFit: LocalModelMemoryFit,
    val status: LocalModelCatalogStatus
)

/** Where a curated download stands. */
sealed class LocalModelCatalogStatus {
    /** Not on the device; [partialBytes] > 0 means a previous attempt can resume. */
    data class Available(val partialBytes: Long) : LocalModelCatalogStatus()

    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : LocalModelCatalogStatus()

    data object Verifying : LocalModelCatalogStatus()

    data class Failed(val failure: LocalModelDownloadFailure, val partialBytes: Long) : LocalModelCatalogStatus()

    /** The exact file is already stored. */
    data object Stored : LocalModelCatalogStatus()
}

/** Progress of a GGUF import started from this screen. */
sealed class LocalModelsImportState {
    data object Idle : LocalModelsImportState()

    /** Copying; [percent] is null when the file size is unknown. */
    data class Copying(val percent: Int?) : LocalModelsImportState()
}

/** Dialogs of the models screen; at most one at a time. */
sealed class LocalModelsDialogState {
    data object None : LocalModelsDialogState()

    /** Explains what a download reveals to Hugging Face before anything is sent. */
    /** @property disguised disguise is on, so the dialog warns that the notification names the app (feat/disguise) */
    data class ConfirmDownload(val key: String, val name: String, val sizeBytes: Long, val disguised: Boolean = false) : LocalModelsDialogState()

    /** Deleting a model; [providerCount] configurations point to it. */
    data class ConfirmDelete(val id: String, val name: String, val providerCount: Int) : LocalModelsDialogState()
}
