// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.translationsettings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.translationsettings.presentation.TranslationSettingsUiIntent
import me.kafuuneko.rpclient.feature.translationsettings.presentation.TranslationSettingsUiState
import me.kafuuneko.rpclient.feature.translationsettings.presentation.TranslationSettingsViewEvent
import me.kafuuneko.rpclient.feature.translationsettings.ui.TranslationSettingsLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** Host of the Translation settings screen, opened from the main settings page. */
class TranslationSettingsActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<TranslationSettingsViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is TranslationSettingsUiState.Finished) finish()
        }

        TranslationSettingsLayout(uiState = uiState, emit = { mViewModel.emit(this) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(TranslationSettingsUiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(TranslationSettingsUiIntent.Resume)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            is TranslationSettingsViewEvent.OpenExternalLink -> openExternalLink(viewEvent.url)
            else -> super.onReceivedViewEvent(viewEvent)
        }
    }

    /** Opens the link in the user's browser, outside the app; no browser shows a short notice. */
    private fun openExternalLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.translation_settings_no_browser, Toast.LENGTH_SHORT).show()
        }
    }
}
