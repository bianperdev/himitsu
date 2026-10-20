// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.lock.ConfirmCredentialContract
import me.kafuuneko.rpclient.feature.main.MainActivity
import me.kafuuneko.rpclient.feature.lock.settings.presentation.SecuritySettingsUiIntent
import me.kafuuneko.rpclient.feature.lock.settings.presentation.SecuritySettingsUiState
import me.kafuuneko.rpclient.feature.lock.settings.presentation.SecuritySettingsViewEvent
import me.kafuuneko.rpclient.feature.lock.settings.ui.SecuritySettingsLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent
import me.kafuuneko.rpclient.libs.security.lock.WindowProtection

/** Host of the security settings screen, opened from the main settings page. */
class SecuritySettingsActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<SecuritySettingsViewModel>()
    private val mConfirmLauncher = registerForActivityResult(ConfirmCredentialContract()) { confirmed ->
        mViewModel.emit(SecuritySettingsUiIntent.ConfirmationFinished(confirmed))
    }
    private val mExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        mViewModel.emit(SecuritySettingsUiIntent.ExportSetAsideTo(uri))
    }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is SecuritySettingsUiState.Finished) finish()
        }

        SecuritySettingsLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(SecuritySettingsUiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(SecuritySettingsUiIntent.Resume)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            SecuritySettingsViewEvent.ApplyWindowProtection -> WindowProtection.apply(this)
            SecuritySettingsViewEvent.RequestConfirmation -> mConfirmLauncher.launch(Unit)
            is SecuritySettingsViewEvent.PickExportDestination -> mExportLauncher.launch(viewEvent.fileName)
            SecuritySettingsViewEvent.RestartApp -> restartApp()
            else -> super.onReceivedViewEvent(viewEvent)
        }
    }

    /**
     * Ends this process and opens the app again in a new one. The restore is recorded on disk
     * and finished at the next start, so nothing is lost if the restart does not happen.
     */
    private fun restartApp() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finishAffinity()
        Runtime.getRuntime().exit(0)
    }
}
