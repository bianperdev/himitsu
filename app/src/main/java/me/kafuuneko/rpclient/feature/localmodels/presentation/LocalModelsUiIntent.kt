// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.localmodels.presentation

import android.net.Uri

/** User intents of the models screen. */
sealed class LocalModelsUiIntent {
    data object Init : LocalModelsUiIntent()

    data object Resume : LocalModelsUiIntent()

    data object Back : LocalModelsUiIntent()

    data object ImportClick : LocalModelsUiIntent()

    data class ImportResult(val uri: Uri?) : LocalModelsUiIntent()

    data class DownloadClick(val key: String) : LocalModelsUiIntent()

    data object ConfirmDownload : LocalModelsUiIntent()

    data class CancelDownload(val key: String) : LocalModelsUiIntent()

    data class DeleteClick(val id: String) : LocalModelsUiIntent()

    data object ConfirmDelete : LocalModelsUiIntent()

    data object DismissDialog : LocalModelsUiIntent()
}
