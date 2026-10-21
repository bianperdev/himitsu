// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.translationsettings.provideredit

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.translationsettings.provideredit.presentation.TranslationProviderEditUiIntent
import me.kafuuneko.rpclient.feature.translationsettings.provideredit.presentation.TranslationProviderEditUiState
import me.kafuuneko.rpclient.feature.translationsettings.provideredit.ui.TranslationProviderEditLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

/** Host of the translation service editor; [EXTRA_PROVIDER_ID] edits a stored service. */
class TranslationProviderEditActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<TranslationProviderEditViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is TranslationProviderEditUiState.Finished) finish()
        }

        TranslationProviderEditLayout(uiState = uiState, emit = { mViewModel.emit(this) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val providerId = intent.getLongExtra(EXTRA_PROVIDER_ID, 0L).takeIf { it > 0L }
        mViewModel.emit(TranslationProviderEditUiIntent.Init(providerId))
    }

    companion object {
        const val EXTRA_PROVIDER_ID = "extra_translation_provider_id"
    }
}
