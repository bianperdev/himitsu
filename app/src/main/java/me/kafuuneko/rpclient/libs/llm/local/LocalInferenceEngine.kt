// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * An inference engine that runs a model on this device.
 *
 * Engines sit below [LocalLLMClient], so the prompt pipeline (presets, macros, lorebooks, Regex,
 * budget trimming, Prompt Inspector) runs before them unchanged.
 *
 * - Every call runs off the main thread; implementations use their own serialized dispatcher
 *   because native contexts are not thread-safe.
 * - Callers never load two models at once: [LocalModelRuntime] owns the loaded model.
 * - [generate] is a cold flow; cancelling its collector must stop decoding at the next token.
 * - Implementations must never log prompts, responses or model file paths.
 */
interface LocalInferenceEngine {
    /** Which implementation this is. */
    val kind: LocalEngineKind

    /** Current lifecycle state, for diagnostics and the model management screen. */
    val state: StateFlow<LocalEngineState>

    /** What this engine can do on this device. */
    fun capabilities(): LocalEngineCapabilities

    /** Names of the GPUs this engine can offload to on this device; empty when there are none. */
    suspend fun gpuDevices(): List<String> = emptyList()

    /**
     * Loads [model] and makes it ready for [generate]. Loading another model first releases the
     * current one.
     */
    suspend fun load(model: LocalModelDescriptor, config: LocalLoadConfig)

    /** Releases the weights and the KV cache. Does nothing when no model is loaded. */
    suspend fun unload()

    /** Formats chat messages into the prompt text the loaded model expects. */
    suspend fun renderChat(messages: List<LocalChatMessage>): RenderedPrompt

    /** Counts the tokens of [text] with the loaded model's tokenizer, or estimates them. */
    suspend fun countTokens(text: String): Int

    /** Counts the tokens of a rendered prompt exactly as [generate] would feed them. */
    suspend fun countPromptTokens(prompt: RenderedPrompt): Int = countTokens(prompt.text)

    /**
     * Asks a running generation to stop at the next token, as if its collector were cancelled, but
     * from outside the coroutine (thermal shutdown, memory pressure). Safe from any thread.
     */
    fun abortGeneration() = Unit

    /** Streams the model's reply to [prompt]; ends with exactly one [LocalGenerationEvent.Finished]. */
    fun generate(prompt: RenderedPrompt, sampling: LocalSamplingParams): Flow<LocalGenerationEvent>
}

/** Engine implementations. The name is persisted, so existing members must not be renamed. */
enum class LocalEngineKind {
    /** Streams canned text; registered only in debuggable builds. */
    Fake,

    /** llama.cpp built from source; available in every flavor. */
    LlamaCpp
}

/** Lifecycle of an engine. */
sealed class LocalEngineState {
    data object Idle : LocalEngineState()
    data class Loading(val modelId: String) : LocalEngineState()
    data class Ready(val modelId: String) : LocalEngineState()
    data class Generating(val modelId: String) : LocalEngineState()
}

/** Compute units an engine may use. */
enum class LocalComputeBackend {
    Cpu,
    Gpu,
    Npu
}

/** What an engine supports on the current device. */
data class LocalEngineCapabilities(
    /** Compute units available on this device. */
    val backends: Set<LocalComputeBackend>,
    /** True when [LocalInferenceEngine.countTokens] uses the model's own tokenizer. */
    val hasExactTokenizer: Boolean,
    /** True when [LocalInferenceEngine.renderChat] uses the template bundled with the model. */
    val usesModelChatTemplate: Boolean
)

/** A model the user can pick for a local provider. */
data class LocalModelDescriptor(
    /** Stable identifier stored in the provider's `model` field. */
    val id: String,
    /** Name shown to the user. */
    val displayName: String,
    /** Engine that runs this model. */
    val engine: LocalEngineKind,
    /** Context length the model was trained with; 0 when unknown. */
    val trainedContextTokens: Int = 0,
    /** Model file on this device, for engines that load files; never logged. */
    val filePath: String? = null
)

/** Settings that apply while a model stays loaded. */
data class LocalLoadConfig(
    /** Context window in tokens, shared by the prompt and the reply. */
    val contextTokens: Int,
    /** CPU threads; 0 lets the engine choose. */
    val threads: Int = 0,
    /** Preferred compute unit; engines fall back to the CPU when it is unavailable. */
    val backend: LocalComputeBackend = LocalComputeBackend.Cpu,
    /** 8-bit KV cache instead of 16-bit, to halve its memory. */
    val quantizedKvCache: Boolean = false
)

/** Chat roles understood by local chat templates. */
enum class LocalChatRole {
    System,
    User,
    Assistant
}

/** One already finalized chat message. Local engines accept text only. */
data class LocalChatMessage(
    val role: LocalChatRole,
    val content: String
)

/** The exact text sent to the model, plus how it was produced, for the Prompt Inspector. */
data class RenderedPrompt(
    /** Prompt text after the chat template was applied. */
    val text: String,
    /** Template name, or null when it has no known name. */
    val templateName: String?,
    /** Where the template came from. */
    val templateSource: ChatTemplateSource,
    /**
     * [text] split by origin. Segments that hold message content are tokenized without parsing
     * special tokens, so chat text cannot inject control tokens.
     */
    val segments: List<PromptSegment> = listOf(PromptSegment(text, isMessageContent = false))
)

/** A piece of a rendered prompt. */
data class PromptSegment(
    val text: String,
    /** True when the text came from a chat message rather than from the template itself. */
    val isMessageContent: Boolean
)

/** Origin of the chat template that produced a [RenderedPrompt]. */
enum class ChatTemplateSource {
    /** Template bundled in the model file. */
    Model,

    /** Template chosen by the user in the provider settings. */
    UserOverride,

    /** Built-in fallback used when the model has no usable template. */
    Fallback
}

/** Sampling settings for one generation. */
data class LocalSamplingParams(
    val maxTokens: Int,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val minP: Float? = null,
    val repeatPenalty: Float? = null,
    val seed: Long? = null,
    val stop: List<String> = emptyList()
)

/** Events streamed by [LocalInferenceEngine.generate]. */
sealed class LocalGenerationEvent {
    /** A decoded piece of the reply. */
    data class Token(val text: String) : LocalGenerationEvent()

    /** Generation ended normally. */
    data class Finished(
        val reason: LocalFinishReason,
        val promptTokens: Int,
        val completionTokens: Int
    ) : LocalGenerationEvent()
}

/** Why a generation ended. */
enum class LocalFinishReason(
    /** Finish reason in the vocabulary the rest of the app already understands. */
    val wireName: String
) {
    /** The model emitted an end-of-turn token or a stop sequence. */
    Stop("stop"),

    /** The reply reached `maxTokens`. */
    Length("length")
}
