// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.localmodels

import android.content.Context
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelCatalogItem
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelCatalogStatus
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelItem
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelsDialogState
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelsImportState
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelsUiIntent
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelsUiState
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelsViewEvent
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalPerformanceState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.disguise.isDisguiseActive
import me.kafuuneko.rpclient.libs.llm.local.LocalEngineKind
import me.kafuuneko.rpclient.libs.llm.local.LocalEngineRegistry
import me.kafuuneko.rpclient.libs.llm.local.LocalGpuStatus
import me.kafuuneko.rpclient.libs.llm.local.LocalInferencePrefs
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelCatalog
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelDetails
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelDownloadService
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelDownloadState
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelDownloadTracker
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelDownloader
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelImportException
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelImportFailure
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelImporter
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelLibrary
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelMemoryEstimator
import me.kafuuneko.rpclient.libs.llm.local.model.ggufFileTypeLabel
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * State holder of the on-device models screen.
 *
 * - Lists stored models with memory estimates and deletes them, warning about configurations
 *   that use them.
 * - Imports GGUF files through the Storage Access Framework.
 * - Shows the curated catalog and starts, follows and cancels downloads; the download itself
 *   runs in [LocalModelDownloadService] and reports through [LocalModelDownloadTracker].
 */
class LocalModelsViewModel : CoreViewModelWithEvent<LocalModelsUiIntent, LocalModelsUiState>(
    LocalModelsUiState.None
), KoinComponent {
    private val mContext by inject<Context>()
    private val mLibrary by inject<LocalModelLibrary>()
    private val mImporter by inject<LocalModelImporter>()
    private val mDownloader by inject<LocalModelDownloader>()
    private val mTracker by inject<LocalModelDownloadTracker>()
    private val mLLMRepository by inject<LLMRepository>()
    private val mEngines by inject<LocalEngineRegistry>()

    private var mImportJob: Job? = null
    private var mKnownDownloads: Set<String> = emptySet()

    /** Shows the screen, loads the lists and starts following downloads. */
    @UiIntentObserver(LocalModelsUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<LocalModelsUiState.None>()) return
        LocalModelsUiState.Normal(models = emptyList(), catalog = emptyList(), performance = readPerformance(LocalGpuStatus.None)).setup()
        refresh()
        // Asking the engine may load native code, so it happens after the first frame
        refreshGpu()
        viewModelScope.launch {
            mTracker.states.collect { states ->
                // A download that leaves the tracker finished or was cancelled: the list may have changed
                val finished = mKnownDownloads - states.keys
                mKnownDownloads = states.keys
                if (finished.isNotEmpty()) refresh() else refreshCatalog()
            }
        }
    }

    /** Reloads when the screen comes back, for example after an import elsewhere. */
    @UiIntentObserver(LocalModelsUiIntent.Resume::class)
    private suspend fun onResume() {
        if (!isStateOf<LocalModelsUiState.Normal>()) return
        refresh()
    }

    @UiIntentObserver(LocalModelsUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<LocalModelsUiState.Finished>()) return
        LocalModelsUiState.finished(uiStateFlow.value).setup()
    }

    /** Opens the system file picker for a GGUF model. */
    @UiIntentObserver(LocalModelsUiIntent.ImportClick::class)
    private fun onImportClick() {
        if (mImportJob?.isActive == true) return
        LocalModelsViewEvent.OpenModelPicker.tryEmit()
    }

    /**
     * Copies the picked GGUF into app storage.
     *
     * - Progress updates only when the percentage changes.
     * - Leaving the screen cancels the copy and the importer deletes the partial file.
     */
    @UiIntentObserver(LocalModelsUiIntent.ImportResult::class)
    private fun onImportResult(intent: LocalModelsUiIntent.ImportResult) {
        val uri = intent.uri ?: return
        if (mImportJob?.isActive == true) return
        setImportState(LocalModelsImportState.Copying(percent = null))
        mImportJob = viewModelScope.launch {
            var shownPercent: Int? = null
            try {
                mImporter.import(uri) { copied, total ->
                    val percent = if (total > 0) (copied * 100 / total).toInt().coerceIn(0, 100) else null
                    if (percent != null && percent != shownPercent) {
                        shownPercent = percent
                        viewModelScope.launch { setImportState(LocalModelsImportState.Copying(percent)) }
                    }
                }
                AppViewEvent.PopupToastMessageByResId(R.string.local_inference_import_done).tryEmit()
                refresh()
            } catch (error: LocalModelImportException) {
                AppViewEvent.PopupToastMessageByResId(error.failure.messageRes()).tryEmit()
            } finally {
                setImportState(LocalModelsImportState.Idle)
            }
        }
    }

    /** Asks for confirmation before anything is sent to Hugging Face. */
    @UiIntentObserver(LocalModelsUiIntent.DownloadClick::class)
    private fun onDownloadClick(intent: LocalModelsUiIntent.DownloadClick) {
        val uiState = getOrNull<LocalModelsUiState.Normal>() ?: return
        val entry = LocalModelCatalog.find(intent.key) ?: return
        if (mTracker.isBusy) {
            AppViewEvent.PopupToastMessageByResId(R.string.local_models_download_busy).tryEmit()
            return
        }
        uiState.copy(
            dialogState = LocalModelsDialogState.ConfirmDownload(entry.key, entry.displayName, entry.sizeBytes, disguised = isDisguiseActive())
        ).setup()
    }

    /** The user accepted the download notice; the Activity starts the service. */
    @UiIntentObserver(LocalModelsUiIntent.ConfirmDownload::class)
    private fun onConfirmDownload() {
        val uiState = getOrNull<LocalModelsUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? LocalModelsDialogState.ConfirmDownload ?: return
        uiState.copy(dialogState = LocalModelsDialogState.None).setup()
        LocalModelsViewEvent.StartDownload(dialog.key).tryEmit()
    }

    /** Stops a running download and deletes its partial file. */
    @UiIntentObserver(LocalModelsUiIntent.CancelDownload::class)
    private fun onCancelDownload(intent: LocalModelsUiIntent.CancelDownload) {
        LocalModelDownloadService.cancel(mContext, intent.key)
    }

    /** Shows the delete confirmation, counting the configurations that use the model. */
    @UiIntentObserver(LocalModelsUiIntent.DeleteClick::class)
    private suspend fun onDeleteClick(intent: LocalModelsUiIntent.DeleteClick) {
        val uiState = getOrNull<LocalModelsUiState.Normal>() ?: return
        val model = uiState.models.firstOrNull { it.id == intent.id } ?: return
        val users = mLLMRepository.getAllProviders()
            .count { it.protocol == LLMProviderProtocol.Local && it.model == model.id }
        uiState.copy(dialogState = LocalModelsDialogState.ConfirmDelete(model.id, model.name, users)).setup()
    }

    /** Deletes the confirmed model; any loaded model is released first. */
    @UiIntentObserver(LocalModelsUiIntent.ConfirmDelete::class)
    private suspend fun onConfirmDelete() {
        val uiState = getOrNull<LocalModelsUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? LocalModelsDialogState.ConfirmDelete ?: return
        uiState.copy(dialogState = LocalModelsDialogState.None).setup()
        mLibrary.delete(dialog.id)
        AppViewEvent.PopupToastMessageByResId(R.string.local_models_deleted).tryEmit()
        refresh()
    }

    @UiIntentObserver(LocalModelsUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<LocalModelsUiState.Normal>() ?: return
        uiState.copy(dialogState = LocalModelsDialogState.None).setup()
    }

    /** Settings take effect at the next generation, which reloads the model with them. */
    @UiIntentObserver(LocalModelsUiIntent.ChangeThreads::class)
    private fun onChangeThreads(intent: LocalModelsUiIntent.ChangeThreads) =
        updatePerformance { LocalInferencePrefs.threads = intent.threads }

    /** Turning the GPU on is the only way its driver gets loaded, also to retry after a failure. */
    @UiIntentObserver(LocalModelsUiIntent.ToggleGpu::class)
    private suspend fun onToggleGpu(intent: LocalModelsUiIntent.ToggleGpu) {
        updatePerformance {
            LocalInferencePrefs.useGpu = intent.enabled
            if (intent.enabled) LocalInferencePrefs.gpuDriverFailed = false
        }
        if (intent.enabled) refreshGpu()
    }

    private suspend fun refreshGpu() {
        val gpu = mEngines.find(LocalEngineKind.LlamaCpp)?.gpuStatus() ?: LocalGpuStatus.None
        getOrNull<LocalModelsUiState.Normal>()?.let { it.copy(performance = readPerformance(gpu)).setup() }
    }

    @UiIntentObserver(LocalModelsUiIntent.ToggleQuantizedKvCache::class)
    private fun onToggleQuantizedKvCache(intent: LocalModelsUiIntent.ToggleQuantizedKvCache) =
        updatePerformance { LocalInferencePrefs.quantizedKvCache = intent.enabled }

    @UiIntentObserver(LocalModelsUiIntent.ChangeUnloadDelay::class)
    private fun onChangeUnloadDelay(intent: LocalModelsUiIntent.ChangeUnloadDelay) =
        updatePerformance { LocalInferencePrefs.unloadDelaySeconds = intent.seconds }

    private fun updatePerformance(change: () -> Unit) {
        val uiState = getOrNull<LocalModelsUiState.Normal>() ?: return
        change()
        uiState.copy(performance = readPerformance(uiState.performance.gpu)).setup()
    }

    private fun readPerformance(gpu: LocalGpuStatus) = LocalPerformanceState(
        threads = LocalInferencePrefs.threads,
        gpu = gpu,
        useGpu = LocalInferencePrefs.useGpu,
        quantizedKvCache = LocalInferencePrefs.quantizedKvCache,
        unloadDelaySeconds = LocalInferencePrefs.unloadDelaySeconds
    )

    override fun onCleared() {
        mImportJob?.cancel()
        super.onCleared()
    }

    private suspend fun refresh() {
        val models = mLibrary.list().map { it.toItem() }
        val uiState = getOrNull<LocalModelsUiState.Normal>() ?: return
        uiState.copy(models = models).setup()
        refreshCatalog()
    }

    /** Rebuilds catalog statuses from what is stored, what is downloading and partial files. */
    private suspend fun refreshCatalog() {
        val stored = mLibrary.storedCatalogKeys()
        val states = mTracker.states.value
        val totalRam = mLibrary.totalRamBytes
        val catalog = withContext(Dispatchers.IO) {
            LocalModelCatalog.entries.map { entry ->
                val partial = mDownloader.partialBytes(entry)
                val status = when (val state = states[entry.key]) {
                    is LocalModelDownloadState.Running ->
                        LocalModelCatalogStatus.Downloading(state.downloadedBytes, state.totalBytes)
                    LocalModelDownloadState.Verifying -> LocalModelCatalogStatus.Verifying
                    is LocalModelDownloadState.Failed -> LocalModelCatalogStatus.Failed(state.failure, partial)
                    null -> if (entry.key in stored) {
                        LocalModelCatalogStatus.Stored
                    } else {
                        LocalModelCatalogStatus.Available(partial)
                    }
                }
                LocalModelCatalogItem(
                    key = entry.key,
                    name = entry.displayName,
                    descriptionRes = entry.descriptionRes,
                    sizeBytes = entry.sizeBytes,
                    license = entry.license,
                    approximateRamBytes = entry.approximateRamBytes,
                    memoryFit = LocalModelMemoryEstimator.fit(entry.approximateRamBytes, totalRam),
                    status = status
                )
            }
        }
        val uiState = getOrNull<LocalModelsUiState.Normal>() ?: return
        uiState.copy(catalog = catalog).setup()
    }

    private fun setImportState(state: LocalModelsImportState) {
        val uiState = getOrNull<LocalModelsUiState.Normal>() ?: return
        uiState.copy(importState = state).setup()
    }

    private fun LocalModelDetails.toItem() = LocalModelItem(
        id = entity.id,
        name = entity.displayName,
        sizeBytes = entity.sizeBytes,
        quantization = ggufFileTypeLabel(entity.fileType),
        trainedContextTokens = entity.trainedContextTokens,
        source = entity.source,
        sha256Prefix = entity.sha256.take(SHA_PREFIX_LENGTH),
        estimatedRamBytes = estimatedRamBytes,
        memoryFit = memoryFit
    )

    private fun LocalModelImportFailure.messageRes(): Int = when (this) {
        LocalModelImportFailure.NotGguf -> R.string.local_inference_import_not_gguf
        LocalModelImportFailure.Unreadable -> R.string.local_inference_import_unreadable
        LocalModelImportFailure.NoSpace -> R.string.local_inference_import_no_space
    }

    private companion object {
        const val SHA_PREFIX_LENGTH = 12
    }
}
