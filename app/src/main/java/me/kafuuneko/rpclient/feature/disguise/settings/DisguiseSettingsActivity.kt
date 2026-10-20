// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseSettingsUiIntent
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseSettingsUiState
import me.kafuuneko.rpclient.feature.disguise.settings.presentation.DisguiseSettingsViewEvent
import me.kafuuneko.rpclient.feature.disguise.settings.ui.DisguiseSettingsLayout
import me.kafuuneko.rpclient.feature.lock.settings.SecuritySettingsActivity
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** Host of the disguise settings screen, opened from the main settings page. */
class DisguiseSettingsActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<DisguiseSettingsViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is DisguiseSettingsUiState.Finished) finish()
        }

        DisguiseSettingsLayout(uiState = uiState, emit = { mViewModel.emit(this) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(DisguiseSettingsUiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(DisguiseSettingsUiIntent.Resume)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            DisguiseSettingsViewEvent.OpenSecuritySettings -> startActivity(Intent(this, SecuritySettingsActivity::class.java))
            DisguiseSettingsViewEvent.CloseApp -> finishAffinity()
            else -> super.onReceivedViewEvent(viewEvent)
        }
    }
}
