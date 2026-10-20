// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.localmodels.ui

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelCatalogItem
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelCatalogStatus
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelItem
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelsDialogState
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelsImportState
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelsUiIntent
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelsUiState
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalPerformanceState
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelDownloadFailure
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelMemoryFit
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelSource
import me.kafuuneko.rpclient.ui.dialog.AppConfirmDialog
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.theme.HimitsuMonoFamily
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader

/** On-device models screen: stored models, GGUF import and the curated downloads. */
@Composable
fun LocalModelsLayout(
    uiState: LocalModelsUiState,
    emit: LocalModelsUiIntent.() -> Unit
) {
    val state = uiState as? LocalModelsUiState.Normal ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.local_models_title),
            onBack = { LocalModelsUiIntent.Back.emit() }
        )
        RpLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                RpPageTitle(
                    title = stringResource(R.string.local_models_title),
                    subtitle = stringResource(R.string.local_models_subtitle)
                )
            }
            item { RpSectionHeader(title = stringResource(R.string.local_models_stored_header)) }
            item { ImportRow(state.importState, emit) }
            if (state.models.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.local_models_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.models, key = { it.id }) { model -> StoredModelCard(model, emit) }
            item { RpSectionHeader(title = stringResource(R.string.local_models_performance_header)) }
            item { PerformanceCard(state.performance, emit) }
            item { RpSectionHeader(title = stringResource(R.string.local_models_catalog_header)) }
            item {
                Text(
                    text = stringResource(R.string.local_models_catalog_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(state.catalog, key = { it.key }) { entry -> CatalogCard(entry, emit) }
        }
    }
    Dialogs(state.dialogState, emit)
}

@Composable
private fun ImportRow(state: LocalModelsImportState, emit: LocalModelsUiIntent.() -> Unit) {
    when (state) {
        LocalModelsImportState.Idle -> OutlinedButton(
            onClick = { LocalModelsUiIntent.ImportClick.emit() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.local_inference_import_button))
        }

        is LocalModelsImportState.Copying -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val percent = state.percent
            Text(
                text = percent?.let { stringResource(R.string.local_inference_importing_percent, it) }
                    ?: stringResource(R.string.local_inference_importing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (percent == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StoredModelCard(model: LocalModelItem, emit: LocalModelsUiIntent.() -> Unit) {
    ModelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { LocalModelsUiIntent.DeleteClick(model.id).emit() }) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.local_models_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val details = listOfNotNull(
            fileSize(model.sizeBytes),
            model.quantization,
            model.trainedContextTokens.takeIf { it > 0 }
                ?.let { stringResource(R.string.local_models_context, it) },
            stringResource(
                when (model.source) {
                    LocalModelSource.Imported -> R.string.local_models_source_imported
                    LocalModelSource.Downloaded -> R.string.local_models_source_downloaded
                }
            )
        )
        Text(
            text = details.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MemoryLine(model.estimatedRamBytes, model.memoryFit)
        Text(
            text = stringResource(R.string.local_models_sha, model.sha256Prefix),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = HimitsuMonoFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CatalogCard(entry: LocalModelCatalogItem, emit: LocalModelsUiIntent.() -> Unit) {
    ModelCard {
        Text(text = entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = stringResource(entry.descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = listOf(fileSize(entry.sizeBytes), entry.license).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MemoryLine(entry.approximateRamBytes, entry.memoryFit)
        CatalogStatusRow(entry, emit)
    }
}

@Composable
private fun CatalogStatusRow(entry: LocalModelCatalogItem, emit: LocalModelsUiIntent.() -> Unit) {
    when (val status = entry.status) {
        is LocalModelCatalogStatus.Available -> DownloadButton(entry, status.partialBytes, emit)

        is LocalModelCatalogStatus.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator(
                progress = { if (status.totalBytes > 0) status.downloadedBytes.toFloat() / status.totalBytes else 0f },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.local_models_download_progress,
                        fileSize(status.downloadedBytes),
                        fileSize(status.totalBytes)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { LocalModelsUiIntent.CancelDownload(entry.key).emit() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }

        LocalModelCatalogStatus.Verifying -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.local_models_verifying),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is LocalModelCatalogStatus.Failed -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(status.failure.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            DownloadButton(entry, status.partialBytes, emit)
        }

        LocalModelCatalogStatus.Stored -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.local_models_stored),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DownloadButton(entry: LocalModelCatalogItem, partialBytes: Long, emit: LocalModelsUiIntent.() -> Unit) {
    Button(
        onClick = { LocalModelsUiIntent.DownloadClick(entry.key).emit() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        val percent = if (entry.sizeBytes > 0) (partialBytes * 100 / entry.sizeBytes).toInt() else 0
        Text(
            if (partialBytes > 0) {
                stringResource(R.string.local_models_resume, percent)
            } else {
                stringResource(R.string.local_models_download)
            }
        )
    }
}

@Composable
private fun MemoryLine(ramBytes: Long, fit: LocalModelMemoryFit) {
    val (labelRes, color) = when (fit) {
        LocalModelMemoryFit.Comfortable -> R.string.local_models_fit_comfortable to MaterialTheme.colorScheme.onSurfaceVariant
        LocalModelMemoryFit.Tight -> R.string.local_models_fit_tight to MaterialTheme.colorScheme.tertiary
        LocalModelMemoryFit.Risky -> R.string.local_models_fit_risky to MaterialTheme.colorScheme.error
        LocalModelMemoryFit.TooLarge -> R.string.local_models_fit_too_large to MaterialTheme.colorScheme.error
    }
    Text(
        text = stringResource(R.string.local_models_ram, fileSize(ramBytes), stringResource(labelRes)),
        style = MaterialTheme.typography.bodySmall,
        color = color
    )
}

@Composable
private fun PerformanceCard(state: LocalPerformanceState, emit: LocalModelsUiIntent.() -> Unit) {
    ModelCard {
        SettingTitle(R.string.local_models_threads, R.string.local_models_threads_desc)
        ChoiceRow(
            options = THREAD_CHOICES,
            selected = state.threads,
            label = { if (it == 0) stringResource(R.string.local_models_threads_auto) else it.toString() },
            onSelect = { LocalModelsUiIntent.ChangeThreads(it).emit() }
        )
        state.gpuName?.let { gpu ->
            SwitchSetting(
                title = stringResource(R.string.local_models_gpu),
                description = stringResource(R.string.local_models_gpu_desc, gpu),
                checked = state.useGpu,
                onChange = { LocalModelsUiIntent.ToggleGpu(it).emit() }
            )
        }
        SwitchSetting(
            title = stringResource(R.string.local_models_kv),
            description = stringResource(R.string.local_models_kv_desc),
            checked = state.quantizedKvCache,
            onChange = { LocalModelsUiIntent.ToggleQuantizedKvCache(it).emit() }
        )
        SettingTitle(R.string.local_models_unload, R.string.local_models_unload_desc)
        ChoiceRow(
            options = UNLOAD_CHOICES,
            selected = state.unloadDelaySeconds,
            label = {
                when {
                    it == 0 -> stringResource(R.string.local_models_unload_now)
                    it < 0 -> stringResource(R.string.local_models_unload_never)
                    else -> stringResource(R.string.local_models_unload_minutes, it / 60)
                }
            },
            onSelect = { LocalModelsUiIntent.ChangeUnloadDelay(it).emit() }
        )
        Text(
            text = stringResource(R.string.local_models_performance_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingTitle(titleRes: Int, descriptionRes: Int) {
    Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
    Text(
        text = stringResource(descriptionRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ChoiceRow(options: List<Int>, selected: Int, label: @Composable (Int) -> String, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

@Composable
private fun SwitchSetting(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private val THREAD_CHOICES = listOf(0, 2, 4, 6, 8)
private val UNLOAD_CHOICES = listOf(0, 60, 120, 300, -1)

@Composable
private fun ModelCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) { content() }
    }
}

@Composable
private fun Dialogs(dialogState: LocalModelsDialogState, emit: LocalModelsUiIntent.() -> Unit) {
    when (dialogState) {
        LocalModelsDialogState.None -> Unit

        is LocalModelsDialogState.ConfirmDownload -> AppConfirmDialog(
            onDismissRequest = { LocalModelsUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.local_models_download_confirm_title, dialogState.name),
            message = buildString {
                append(stringResource(R.string.local_models_download_confirm_message, fileSize(dialogState.sizeBytes)))
                // feat/disguise: the download notification header shows the application label
                if (dialogState.disguised) {
                    append("\n\n")
                    append(stringResource(R.string.disguise_download_notice))
                }
            },
            confirmText = stringResource(R.string.local_models_download),
            onConfirm = { LocalModelsUiIntent.ConfirmDownload.emit() }
        )

        is LocalModelsDialogState.ConfirmDelete -> AppDangerDialog(
            onDismissRequest = { LocalModelsUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.local_models_delete_title, dialogState.name),
            message = buildString {
                append(stringResource(R.string.local_models_delete_message))
                if (dialogState.providerCount > 0) {
                    append("\n\n")
                    append(stringResource(R.string.local_models_delete_in_use, dialogState.providerCount))
                }
            },
            onConfirm = { LocalModelsUiIntent.ConfirmDelete.emit() }
        )
    }
}

@Composable
private fun fileSize(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

private fun LocalModelDownloadFailure.messageRes(): Int = when (this) {
    LocalModelDownloadFailure.Network -> R.string.local_models_download_failed_network
    LocalModelDownloadFailure.NoSpace -> R.string.local_models_download_failed_no_space
    LocalModelDownloadFailure.Integrity -> R.string.local_models_download_failed_integrity
    LocalModelDownloadFailure.Storage -> R.string.local_models_download_failed_storage
}
