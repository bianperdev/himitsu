// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.core

import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import me.kafuuneko.rpclient.libs.translation.ChatTranslator
import me.kafuuneko.rpclient.libs.translation.LanguageDetector
import me.kafuuneko.rpclient.libs.translation.OutgoingTranslation
import me.kafuuneko.rpclient.libs.translation.TextProtector
import me.kafuuneko.rpclient.libs.translation.TranslatableMessage
import me.kafuuneko.rpclient.libs.translation.TranslationClient
import me.kafuuneko.rpclient.libs.translation.TranslationClientFactory
import me.kafuuneko.rpclient.libs.translation.TranslationException
import me.kafuuneko.rpclient.libs.translation.TranslationFailure
import me.kafuuneko.rpclient.libs.translation.TranslationProviderConfig
import me.kafuuneko.rpclient.libs.translation.TranslationSessionKey
import me.kafuuneko.rpclient.libs.translation.TranslationState
import me.kafuuneko.rpclient.libs.translation.model.ChatMessageTranslationDao
import me.kafuuneko.rpclient.libs.translation.model.ChatTranslationSettingDao
import me.kafuuneko.rpclient.libs.translation.model.GroupChatMessageTranslationDao
import me.kafuuneko.rpclient.libs.translation.model.GroupChatTranslationSettingDao
import me.kafuuneko.rpclient.libs.translation.model.TranslationMode
import me.kafuuneko.rpclient.libs.translation.model.TranslationProvider
import me.kafuuneko.rpclient.libs.translation.model.TranslationProviderDao
import me.kafuuneko.rpclient.libs.translation.model.TranslationProviderType

/**
 * The [ChatTranslator] the chat screens use: decides whether a chat translates, translates and
 * caches display texts, and translates what the user types.
 *
 * - A chat is active only with the master switch on and a usable provider (the chat's own, else
 *   the default). Mode Off never translates; On translates from the stored conversation
 *   language; Auto detects the language of the latest character messages and translates when it
 *   differs from the user's.
 * - Detection runs on the device first; the provider's detection is only asked when the device
 *   cannot tell, translation is already on with a provider, and the chat has no stored
 *   language yet (once stored, it is reused instead of sending samples again).
 * - Display texts are cached per message with the SHA-256 of the content; a different hash
 *   means the content changed, so the row is stale and translated again.
 * - Text is protected with [TextProtector] before it is sent, and a translation whose tokens
 *   cannot be restored is never stored (the original is shown).
 * - Nothing here logs, and no method throws to the caller except on coroutine cancellation.
 */
class ChatTranslatorImpl(
    private val providerDao: TranslationProviderDao,
    chatMessageDao: ChatMessageTranslationDao,
    groupMessageDao: GroupChatMessageTranslationDao,
    chatSettingDao: ChatTranslationSettingDao,
    groupSettingDao: GroupChatTranslationSettingDao,
    private val clientFactory: TranslationClientFactory,
    private val detector: LanguageDetector,
    private val protector: TextProtector,
    private val settings: TranslationCoreSettings,
    private val clock: () -> Long = System::currentTimeMillis
) : ChatTranslator {

    private val store = TranslationStore(chatMessageDao, groupMessageDao, chatSettingDao, groupSettingDao)

    /** Everything one call needs about a chat. */
    private class ChatContext(
        val setting: SessionSetting,
        val provider: TranslationProvider?,
        val userLanguage: String,
        val conversationLanguage: String?,
        val active: Boolean
    ) {
        /** True when there is something to translate between two different languages. */
        val translating: Boolean
            get() = active && provider != null && conversationLanguage != null && conversationLanguage != userLanguage
    }

    /**
     * Decides whether the chat translates, detecting the conversation language again in Auto
     * mode from [recent] and storing it.
     */
    override suspend fun resolve(session: TranslationSessionKey, recent: List<TranslatableMessage>): TranslationState {
        val userLanguage = safeUserLanguage()
        val context = guard(null) { chatContext(session, recent, redetect = true) }
        return TranslationState(
            mode = context?.setting?.mode ?: TranslationMode.Auto,
            active = context?.active ?: false,
            conversationLanguage = context?.conversationLanguage,
            userLanguage = context?.userLanguage ?: userLanguage
        )
    }

    /**
     * Display texts of [messages] by id.
     *
     * - User messages: the stored text the user typed, while it matches the content.
     * - Character messages: the cached translation when fresh; the missing and stale ones are
     *   translated in batches and stored. Only while the chat is translating.
     */
    override suspend fun displayTexts(session: TranslationSessionKey, messages: List<TranslatableMessage>): Map<Long, String> {
        if (messages.isEmpty()) return emptyMap()
        val result = HashMap<Long, String>()
        guard(Unit) {
            // Fresh rows first: what the user typed is shown even when translation is off now
            val stored = store.translations(session, messages.map { it.id }.distinct())
            val hashes = messages.associate { it.id to sha256(it.content) }
            messages.filter { it.fromUser }.forEach { message ->
                stored[message.id]?.takeIf { it.contentHash == hashes[message.id] }?.let { result[message.id] = it.displayText }
            }
            // Character messages only while the chat translates, using the stored language
            val context = chatContext(session, messages, redetect = false)
            if (!context.translating) return@guard
            val pending = mutableListOf<TranslatableMessage>()
            messages.filter { !it.fromUser && it.content.isNotBlank() }.distinctBy { it.id }.forEach { message ->
                val row = stored[message.id]
                if (row != null && row.contentHash == hashes[message.id] && row.displayLanguage == context.userLanguage) {
                    result[message.id] = row.displayText
                } else {
                    pending += message
                }
            }
            translateAndStore(session, context, pending, hashes, result)
        }
        return result
    }

    /**
     * Translates what the user typed into the conversation language when the chat translates
     * and [TranslationCoreSettings.translateOwnMessages] is on; returns [input] unchanged
     * otherwise, and with the failure set when the translation failed.
     */
    override suspend fun outgoing(session: TranslationSessionKey, input: String, names: List<String>): OutgoingTranslation {
        val unchanged = OutgoingTranslation(content = input, displayText = null)
        if (input.isBlank()) return unchanged
        val context = guard(null) { chatContext(session, emptyList(), redetect = false) } ?: return unchanged
        if (!context.translating || !settings.translateOwnMessages) return unchanged
        val provider = context.provider ?: return unchanged
        val source = context.userLanguage
        val target = context.conversationLanguage ?: return unchanged
        return try {
            val protected = protector.protect(input, names)
            val translated = clientFor(provider).translate(listOf(protected.text), source, target).singleOrNull()
                ?: return unchanged.copy(failure = TranslationFailure.Invalid)
            val restored = protector.restore(translated, protected)
                ?: return unchanged.copy(failure = TranslationFailure.Invalid)
            OutgoingTranslation(content = restored, displayText = input)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            unchanged.copy(failure = failureOf(e))
        }
    }

    /** Stores [displayText] as what the user typed for [messageId], matched to [content]. */
    override suspend fun rememberOutgoing(session: TranslationSessionKey, messageId: Long, content: String, displayText: String) {
        guard(Unit) {
            val setting = store.setting(session)
            store.saveTranslation(
                session,
                StoredTranslation(
                    messageId = messageId,
                    displayText = displayText,
                    displayLanguage = safeUserLanguage(),
                    conversationLanguage = setting.conversationLanguage.orEmpty(),
                    contentHash = sha256(content),
                    providerId = setting.providerId ?: settings.defaultProviderId,
                    createTime = clock()
                )
            )
        }
    }

    /** Changes the chat's mode, keeping its language and provider. */
    override suspend fun setMode(session: TranslationSessionKey, mode: TranslationMode) {
        guard(Unit) { store.saveSetting(session, store.setting(session).copy(mode = mode)) }
    }

    /**
     * Gathers the settings, the provider and the conversation language of a chat.
     *
     * - Inactive without the master switch, without a usable provider, or in mode Off.
     * - The language is detected when unknown, and in Auto mode also when [redetect] is set;
     *   a newly detected language is stored.
     */
    private suspend fun chatContext(
        session: TranslationSessionKey,
        recent: List<TranslatableMessage>,
        redetect: Boolean
    ): ChatContext {
        val setting = store.setting(session)
        val userLanguage = safeUserLanguage()
        // Master switch and provider: without them nothing is detected remotely or translated
        val provider = if (settings.enabled) usableProvider(setting) else null
        if (provider == null || setting.mode == TranslationMode.Off) {
            return ChatContext(setting, provider, userLanguage, setting.conversationLanguage, active = false)
        }
        // Language: stored, or detected from the latest character messages
        val shouldDetect = setting.conversationLanguage == null || (redetect && setting.mode == TranslationMode.Auto)
        // The provider only detects a chat whose language is still unknown, so devices without
        // on-device detection (API 26-28) do not send a sample on every resolve
        val detected = if (shouldDetect) {
            detectConversation(recent, provider, askProvider = setting.conversationLanguage == null)
        } else {
            null
        }
        val language = detected ?: setting.conversationLanguage
        if (detected != null && detected != setting.conversationLanguage) {
            store.saveSetting(session, setting.copy(conversationLanguage = detected))
        }
        // On translates whenever the language is known; Auto only when it differs from the user's
        val active = when (setting.mode) {
            TranslationMode.On -> language != null
            TranslationMode.Auto -> language != null && language != userLanguage
            TranslationMode.Off -> false
        }
        return ChatContext(setting.copy(conversationLanguage = language), provider, userLanguage, language, active)
    }

    /** The chat's provider, else the default one, when it exists and is configured enough to use. */
    private suspend fun usableProvider(setting: SessionSetting): TranslationProvider? {
        val id = setting.providerId ?: settings.defaultProviderId ?: return null
        val provider = providerDao.getById(id) ?: return null
        val usable = when (provider.type) {
            TranslationProviderType.LibreTranslate -> provider.baseUrl.isNotBlank()
            TranslationProviderType.LlmProvider -> provider.llmProviderId != null
            TranslationProviderType.DeepL, TranslationProviderType.GoogleCloud -> true
        }
        return provider.takeIf { usable }
    }

    /**
     * The language of the latest character messages: several joined, short ones ignored, with
     * names, macros and markup removed; null when there is not enough text or nobody can tell.
     *
     * @param askProvider whether the provider may detect when the device cannot.
     */
    private suspend fun detectConversation(
        recent: List<TranslatableMessage>,
        provider: TranslationProvider,
        askProvider: Boolean
    ): String? {
        // Build a sample from the newest substantial character messages
        val parts = recent.asSequence()
            .filter { !it.fromUser }
            .map { detectionText(it) }
            .filter { it.length >= MIN_DETECTION_MESSAGE_CHARS }
            .toList()
            .takeLast(MAX_DETECTION_MESSAGES)
        val sample = parts.joinToString("\n\n").take(MAX_DETECTION_CHARS)
        if (sample.length < MIN_DETECTION_SAMPLE_CHARS) return null
        // On the device first; the provider only when the device cannot tell
        val local = runCatchingNotCancelled { normalizeLanguage(detector.detect(sample)) }
        if (local != null || !askProvider) return local
        return runCatchingNotCancelled { normalizeLanguage(clientFor(provider).detect(sample)) }
    }

    /** [message] without its protected spans, which say nothing about the language. */
    private fun detectionText(message: TranslatableMessage): String {
        val protected = protector.protect(message.content, message.names)
        var text = protected.text
        protected.tokens.keys.forEach { text = text.replace(it, " ") }
        return WHITESPACE.replace(text, " ").trim()
    }

    /**
     * Translates [pending] character messages in batches, storing and adding to [result] every
     * translation whose tokens were restored. A failed batch leaves its messages out.
     */
    private suspend fun translateAndStore(
        session: TranslationSessionKey,
        context: ChatContext,
        pending: List<TranslatableMessage>,
        hashes: Map<Long, String>,
        result: MutableMap<Long, String>
    ) {
        if (pending.isEmpty()) return
        val provider = context.provider ?: return
        val source = context.conversationLanguage ?: return
        val client = clientFor(provider)
        for (batch in batches(pending)) {
            val protectedTexts = batch.map { protector.protect(it.content, it.names) }
            val translated = runCatchingNotCancelled {
                client.translate(protectedTexts.map { it.text }, source, context.userLanguage)
            } ?: continue
            // A service answering with another number of texts cannot be matched safely
            if (translated.size != batch.size) continue
            batch.forEachIndexed { index, message ->
                val restored = protector.restore(translated[index], protectedTexts[index]) ?: return@forEachIndexed
                store.saveTranslation(
                    session,
                    StoredTranslation(
                        messageId = message.id,
                        displayText = restored,
                        displayLanguage = context.userLanguage,
                        conversationLanguage = source,
                        contentHash = hashes.getValue(message.id),
                        providerId = provider.id,
                        createTime = clock()
                    )
                )
                result[message.id] = restored
            }
        }
    }

    /** Splits [messages] into requests bounded by count and by characters. */
    private fun batches(messages: List<TranslatableMessage>): List<List<TranslatableMessage>> {
        val batches = mutableListOf<List<TranslatableMessage>>()
        var current = mutableListOf<TranslatableMessage>()
        var chars = 0
        for (message in messages) {
            val full = current.size >= MAX_BATCH_TEXTS || (current.isNotEmpty() && chars + message.content.length > MAX_BATCH_CHARS)
            if (full) {
                batches += current
                current = mutableListOf()
                chars = 0
            }
            current += message
            chars += message.content.length
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }

    private fun clientFor(provider: TranslationProvider): TranslationClient = clientFactory.create(
        TranslationProviderConfig(
            id = provider.id,
            name = provider.name,
            type = provider.type,
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            llmProviderId = provider.llmProviderId,
            customHeadersJson = provider.customHeadersJson
        )
    )

    private fun safeUserLanguage(): String =
        runCatching { settings.userLanguage() }.getOrNull()?.let { normalizeLanguage(it) } ?: FALLBACK_LANGUAGE

    private fun failureOf(error: Throwable): TranslationFailure = when (error) {
        is TranslationException -> error.failure
        is IOException -> TranslationFailure.Network
        else -> TranslationFailure.Invalid
    }

    /** Runs [block], returning [fallback] on any error but cancellation. */
    private inline fun <T> guard(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        fallback
    }

    private inline fun <T> runCatchingNotCancelled(block: () -> T?): T? = guard(null, block)

    internal companion object {
        const val FALLBACK_LANGUAGE = "en"

        /** Messages shorter than this, once cleaned, say too little about their language. */
        const val MIN_DETECTION_MESSAGE_CHARS = 12
        const val MIN_DETECTION_SAMPLE_CHARS = 40
        const val MAX_DETECTION_MESSAGES = 5
        const val MAX_DETECTION_CHARS = 2000
        const val MAX_BATCH_TEXTS = 16
        const val MAX_BATCH_CHARS = 8000

        private val WHITESPACE = Regex("\\s+")

        /** Lower-case hex SHA-256 of [text] in UTF-8, the `contentHash` of translation rows. */
        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
