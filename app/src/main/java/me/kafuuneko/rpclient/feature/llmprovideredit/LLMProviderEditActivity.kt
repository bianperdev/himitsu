package me.kafuuneko.rpclient.feature.llmprovideredit

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiIntent
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditViewEvent
import me.kafuuneko.rpclient.feature.llmprovideredit.ui.LLMProviderEditLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 模型配置创建与编辑页面宿主。 */
class LLMProviderEditActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<LLMProviderEditViewModel>()

    /** Himitsu: the picked URI is used once for the copy; no persistent permission is kept. */
    private val mLocalModelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        mViewModel.emit(LLMProviderEditUiIntent.ImportLocalModelResult(uri))
    }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is LLMProviderEditUiState.Finished) finish()
        }

        LLMProviderEditLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        super.onReceivedViewEvent(viewEvent)
        when (viewEvent) {
            // GGUF has no registered MIME type, so the content is validated after picking
            LLMProviderEditViewEvent.OpenLocalModelPicker -> mLocalModelPicker.launch(arrayOf("*/*"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val providerId = intent.getLongExtra(EXTRA_PROVIDER_ID, 0L).takeIf { it > 0L }
        mViewModel.emit(LLMProviderEditUiIntent.Init(providerId))
    }

    companion object {
        const val EXTRA_PROVIDER_ID = "extra_provider_id"
    }
}
