// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

/**
 * Failures of on-device inference.
 *
 * Messages are fixed English diagnostics: they never include prompts, replies, model names or file
 * paths. The UI maps the type to a localized message.
 */
sealed class LocalInferenceException(message: String) : IllegalStateException(message)

/** The engine's native library is missing or failed to initialize on this device. */
class LocalEngineUnavailableException : LocalInferenceException("The local inference engine is not available")

/** The model file could not be loaded: missing, corrupt, unsupported or too large for memory. */
class LocalModelLoadException : LocalInferenceException("The local model could not be loaded")

/** The model's chat template failed to render the conversation. */
class LocalChatTemplateException : LocalInferenceException("The chat template could not be rendered")

/** The rendered prompt does not fit in the context window. */
class LocalPromptTooLongException(
    val promptTokens: Int
) : LocalInferenceException("The prompt does not fit in the local context window")

/** Decoding failed inside the engine. */
class LocalGenerationException : LocalInferenceException("Local generation failed")
