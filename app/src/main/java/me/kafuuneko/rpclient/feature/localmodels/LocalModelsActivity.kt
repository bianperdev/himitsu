// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.localmodels

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelsUiIntent
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelsUiState
import me.kafuuneko.rpclient.feature.localmodels.presentation.LocalModelsViewEvent
import me.kafuuneko.rpclient.feature.localmodels.ui.LocalModelsLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelDownloadService

/** Host of the on-device models screen. */
class LocalModelsActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<LocalModelsViewModel>()

    /** Download waiting for the notification permission answer. */
    private var mPendingDownloadKey: String? = null

    /** The picked URI is used once for the copy; no persistent permission is kept. */
    private val mModelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        mViewModel.emit(LocalModelsUiIntent.ImportResult(uri))
    }

    /** The download runs whatever the answer; without the permission its notification is hidden. */
    private val mNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        mPendingDownloadKey?.let { LocalModelDownloadService.start(this, it) }
        mPendingDownloadKey = null
    }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is LocalModelsUiState.Finished) finish()
        }

        LocalModelsLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(LocalModelsUiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(LocalModelsUiIntent.Resume)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        super.onReceivedViewEvent(viewEvent)
        when (viewEvent) {
            // GGUF has no registered MIME type, so the content is validated after picking
            LocalModelsViewEvent.OpenModelPicker -> mModelPicker.launch(arrayOf("*/*"))
            is LocalModelsViewEvent.StartDownload -> startDownload(viewEvent.key)
        }
    }

    private fun startDownload(key: String) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            mPendingDownloadKey = key
            mNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            LocalModelDownloadService.start(this, key)
        }
    }
}
