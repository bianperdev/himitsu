// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.kafuuneko.rpclient.libs.llm.ImageRequestException
import me.kafuuneko.rpclient.libs.llm.ImageRequestFailure
import me.kafuuneko.rpclient.libs.llm.LLMClient
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMUsage
import me.kafuuneko.rpclient.libs.prompt.PromptBudgetExceededException

/**
 * [LLMClient] for providers whose model runs on this device.
 *
 * - Receives the same finalized messages as remote clients, so presets, macros, lorebooks,
 *   Regex and budget trimming already happened upstream.
 * - Applies the model's chat template through the engine and streams its tokens as
 *   [LLMStreamEvent.Delta]; the usage reported at the end is the engine's exact count.
 * - Makes no network calls. Text only: a request with images fails like an image-less model.
 */
class LocalLLMClient(
    private val mProvider: LLMProviderConfig,
    private val mRuntime: LocalModelRuntime
) : LLMClient {
    override suspend fun generate(request: LLMGenerationRequest): LLMGenerationResponse {
        val content = StringBuilder()
        var finished: LLMStreamEvent.Finished? = null
        streamGenerate(request).collect { event ->
            when (event) {
                is LLMStreamEvent.Delta -> content.append(event.content)
                is LLMStreamEvent.Finished -> finished = event
                is LLMStreamEvent.ReasoningDelta,
                LLMStreamEvent.Connected -> Unit
            }
        }
        val end = finished
        return LLMGenerationResponse(
            content = content.toString(),
            model = end?.model ?: modelId(request),
            provider = mProvider.providerType,
            usage = end?.usage,
            finishReason = end?.finishReason,
            rawResponse = ""
        )
    }

    override fun streamGenerate(request: LLMGenerationRequest): Flow<LLMStreamEvent> = flow {
        // Fail early on content the engines cannot take, before loading a model
        if (request.messages.any { it.images.isNotEmpty() }) {
            throw ImageRequestException(ImageRequestFailure.Unsupported)
        }
        val modelId = modelId(request)
        val sampling = LocalSamplingParamsResolver.resolve(mProvider, request.options)
        val config = LocalLoadConfig(contextTokens = mProvider.contextTokens)
        emit(LLMStreamEvent.Connected)
        try {
            mRuntime.withModel(modelId, config) { engine, model ->
                val prompt = engine.renderChat(request.messages.toLocalMessages())
                // Cancelling this collector cancels the engine flow, which stops decoding
                engine.generate(prompt, sampling).collect { event ->
                    when (event) {
                        is LocalGenerationEvent.Token -> emit(LLMStreamEvent.Delta(event.text, rawChunk = ""))
                        is LocalGenerationEvent.Finished -> emit(
                            LLMStreamEvent.Finished(
                                finishReason = event.reason.wireName,
                                model = model.id,
                                usage = LLMUsage(
                                    promptTokens = event.promptTokens,
                                    completionTokens = event.completionTokens,
                                    totalTokens = event.promptTokens + event.completionTokens
                                )
                            )
                        )
                    }
                }
            }
        } catch (error: LocalPromptTooLongException) {
            // Same message as the prompt budget check, which only estimated the tokens
            throw PromptBudgetExceededException(
                requiredTokens = error.promptTokens,
                promptBudget = (mProvider.contextTokens - sampling.maxTokens).coerceAtLeast(0)
            )
        }
    }

    private fun modelId(request: LLMGenerationRequest): String {
        return request.model?.takeIf { it.isNotBlank() } ?: mProvider.model
    }

}

/** Converts finalized messages to the text-only messages local engines take. */
internal fun List<LLMMessage>.toLocalMessages(): List<LocalChatMessage> = map { message ->
    LocalChatMessage(
        role = when (message.role) {
            LLMMessageRole.System -> LocalChatRole.System
            LLMMessageRole.User -> LocalChatRole.User
            LLMMessageRole.Assistant -> LocalChatRole.Assistant
        },
        content = message.content
    )
}

/** Creates [LocalLLMClient]s; registered in [localInferenceModule]. */
class LocalLLMClientFactory(private val mRuntime: LocalModelRuntime) {
    /** Returns a client for a provider whose protocol is local. */
    fun create(provider: LLMProviderConfig): LLMClient = LocalLLMClient(provider, mRuntime)
}
