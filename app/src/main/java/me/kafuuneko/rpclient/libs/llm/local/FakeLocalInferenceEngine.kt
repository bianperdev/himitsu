// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * A local engine that streams canned text, used to check the provider integration, the chat UI
 * and the Prompt Inspector without native code or a model file.
 *
 * - Registered only in debuggable builds, see [localInferenceModule].
 * - Renders prompts with ChatML so the rendered-prompt path can be exercised.
 * - The reply only reports sizes of the prompt, never its content.
 */
class FakeLocalInferenceEngine(
    private val mTokenDelayMillis: Long = DEFAULT_TOKEN_DELAY_MILLIS
) : LocalInferenceEngine {
    private val mState = MutableStateFlow<LocalEngineState>(LocalEngineState.Idle)

    override val kind: LocalEngineKind = LocalEngineKind.Fake

    override val state: StateFlow<LocalEngineState> = mState.asStateFlow()

    override fun capabilities(): LocalEngineCapabilities = LocalEngineCapabilities(
        backends = setOf(LocalComputeBackend.Cpu),
        hasExactTokenizer = false,
        usesModelChatTemplate = false
    )

    override suspend fun load(model: LocalModelDescriptor, config: LocalLoadConfig) {
        mState.value = LocalEngineState.Ready(model.id)
    }

    override suspend fun unload() {
        mState.value = LocalEngineState.Idle
    }

    override suspend fun renderChat(messages: List<LocalChatMessage>): RenderedPrompt {
        val text = buildString {
            messages.forEach { message ->
                append("<|im_start|>").append(message.role.name.lowercase()).append('\n')
                append(message.content).append("<|im_end|>\n")
            }
            append("<|im_start|>assistant\n")
        }
        return RenderedPrompt(text, templateName = "chatml", templateSource = ChatTemplateSource.Fallback)
    }

    override suspend fun countTokens(text: String): Int = estimateTokens(text)

    override fun generate(
        prompt: RenderedPrompt,
        sampling: LocalSamplingParams
    ): Flow<LocalGenerationEvent> = flow {
        val modelId = (mState.value as? LocalEngineState.Ready)?.modelId
            ?: error("No model is loaded")
        mState.value = LocalEngineState.Generating(modelId)
        try {
            val words = cannedReply(prompt).split(' ')
            val emitted = words.take(sampling.maxTokens.coerceAtLeast(1))
            // The delay doubles as a cancellation point, like a real decode loop between tokens
            emitted.forEachIndexed { index, word ->
                delay(mTokenDelayMillis)
                emit(LocalGenerationEvent.Token(if (index == 0) word else " $word"))
            }
            val reason = if (emitted.size < words.size) LocalFinishReason.Length else LocalFinishReason.Stop
            emit(
                LocalGenerationEvent.Finished(
                    reason = reason,
                    promptTokens = estimateTokens(prompt.text),
                    completionTokens = emitted.size
                )
            )
        } finally {
            mState.value = LocalEngineState.Ready(modelId)
        }
    }.flowOn(Dispatchers.Default)

    private fun cannedReply(prompt: RenderedPrompt): String {
        return "This reply comes from the fake local engine. The rendered prompt has " +
            "${prompt.text.length} characters and about ${estimateTokens(prompt.text)} tokens. " +
            "No model ran and nothing left the device. Press stop while this text is still " +
            "arriving to check that cancellation works, then open the Prompt Inspector to " +
            "compare it with a remote provider."
    }

    private fun estimateTokens(text: String): Int = (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN

    companion object {
        /** Model id of the only model this engine offers. */
        const val MODEL_ID = "himitsu-fake-echo"

        private const val DEFAULT_TOKEN_DELAY_MILLIS = 60L
        private const val CHARS_PER_TOKEN = 4
    }
}
