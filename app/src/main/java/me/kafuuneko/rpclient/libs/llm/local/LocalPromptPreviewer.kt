// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

import android.os.Looper
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.prompt.model.RenderedPromptInspection
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/**
 * Renders finalized messages with the model's chat template for the Prompt Inspector.
 *
 * - Called by `PromptRequestFinalizer`, which is synchronous and runs off the main thread, so
 *   this blocks while the model loads; generation right after reuses the loaded model.
 * - Never blocks the main thread: there it returns null and the Inspector shows no rendered prompt.
 * - Any failure returns null; the generation itself reports errors to the user.
 */
class LocalPromptPreviewer(private val mRuntime: LocalModelRuntime) {
    /** Returns the rendered prompt for [provider], or null when it cannot be produced here. */
    fun preview(provider: LLMProvider, model: String?, messages: List<LLMMessage>): RenderedPromptInspection? {
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        val modelId = model?.takeIf { it.isNotBlank() } ?: provider.model
        val config = LocalInferencePrefs.loadConfig(provider.contextTokens)
        return runCatching {
            runBlocking {
                mRuntime.withModel(modelId, config) { engine, _ ->
                    val prompt = engine.renderChat(messages.toLocalMessages())
                    RenderedPromptInspection(
                        text = prompt.text,
                        usesFallbackTemplate = prompt.templateSource == ChatTemplateSource.Fallback,
                        tokenCount = engine.countPromptTokens(prompt)
                    )
                }
            }
        }.getOrNull()
    }
}
