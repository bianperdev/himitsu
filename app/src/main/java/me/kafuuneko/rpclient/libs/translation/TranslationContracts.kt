// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation

import me.kafuuneko.rpclient.libs.translation.model.TranslationMode
import me.kafuuneko.rpclient.libs.translation.model.TranslationProviderType

// Contracts shared by the translation tracks (see doc/himitsu/translation-design.md). Language
// tags are BCP 47 primary tags in lower case ("en", "es", "pt").

/** A translation service as the clients need it; built from a stored provider. */
data class TranslationProviderConfig(
    val id: Long,
    val name: String,
    val type: TranslationProviderType,
    val baseUrl: String,
    val apiKey: String,
    val llmProviderId: Long?,
    val customHeadersJson: String
) {
    override fun toString(): String = "TranslationProviderConfig(id=$id, type=$type, [REDACTED])"
}

/** Why a translation failed; shown as a quiet notice, never with the text or key. */
enum class TranslationFailure { Network, Auth, Quota, Unsupported, Invalid }

class TranslationException(val failure: TranslationFailure, cause: Throwable? = null) :
    Exception("Translation failed: $failure", cause)

/**
 * One translation service. Implementations never log text, keys or headers, never follow
 * redirects (use the shared OkHttpClient) and throw [TranslationException] on failure.
 */
interface TranslationClient {
    /** Translates [texts], already protected, from [source] (null: the service detects) to [target], in order. */
    suspend fun translate(texts: List<String>, source: String?, target: String): List<String>

    /** The language of [text], or null when the service cannot tell. */
    suspend fun detect(text: String): String?
}

/** Builds the client of a provider. */
fun interface TranslationClientFactory {
    fun create(config: TranslationProviderConfig): TranslationClient
}

/** Language detection on the device; null when unsure or unavailable (API < 29). */
fun interface LanguageDetector {
    suspend fun detect(text: String): String?
}

/** [text] with its protected spans replaced by tokens that [TextProtector.restore] puts back. */
data class ProtectedText(val text: String, val tokens: Map<String, String>) {
    override fun toString(): String = "ProtectedText([REDACTED], tokens=${tokens.size})"
}

/** Shields names, macros, action markers, markdown and URLs from translation. */
interface TextProtector {
    fun protect(text: String, names: List<String>): ProtectedText

    /** The translation with every token restored, or null when a token was lost (show the original). */
    fun restore(translated: String, protected: ProtectedText): String?
}

/** Which chat a translation belongs to. */
sealed class TranslationSessionKey {
    data class Chat(val sessionId: Long) : TranslationSessionKey()
    data class GroupChat(val sessionId: Long) : TranslationSessionKey()
}

/** A message the chat screens show; [content] is in the conversation language. */
data class TranslatableMessage(
    val id: Long,
    val content: String,
    val fromUser: Boolean,
    /** Names to protect: the character's and the user's. */
    val names: List<String> = emptyList()
) {
    override fun toString(): String = "TranslatableMessage(id=$id, fromUser=$fromUser, [REDACTED])"
}

/** Whether a chat translates now, and between which languages. */
data class TranslationState(
    val mode: TranslationMode,
    /** True when messages are being translated for this chat. */
    val active: Boolean,
    val conversationLanguage: String?,
    val userLanguage: String
)

/** What to store and send for the text the user typed. */
data class OutgoingTranslation(
    /** Conversation-language text: the message content the model sees. */
    val content: String,
    /** What the user typed, stored as the display text; null when nothing was translated. */
    val displayText: String?,
    val failure: TranslationFailure? = null
) {
    override fun toString(): String = "OutgoingTranslation([REDACTED], translated=${displayText != null}, failure=$failure)"
}

/** What the chat screens call. Every method is safe to call when translation is off. */
interface ChatTranslator {
    /** Decides, from the latest character messages and the settings, whether this chat translates. */
    suspend fun resolve(session: TranslationSessionKey, recent: List<TranslatableMessage>): TranslationState

    /**
     * Display texts for [messages] in the user's language, by message id; translates and stores
     * the missing or stale ones. A message without an entry is shown as is.
     */
    suspend fun displayTexts(session: TranslationSessionKey, messages: List<TranslatableMessage>): Map<Long, String>

    /** Conversation-language text for what the user typed; the input unchanged when not translating. */
    suspend fun outgoing(session: TranslationSessionKey, input: String, names: List<String>): OutgoingTranslation

    /** Stores what the user typed for the message [messageId], after it was saved with the translated content. */
    suspend fun rememberOutgoing(session: TranslationSessionKey, messageId: Long, content: String, displayText: String)

    /** Changes the chat's mode (Auto, On, Off). */
    suspend fun setMode(session: TranslationSessionKey, mode: TranslationMode)
}
