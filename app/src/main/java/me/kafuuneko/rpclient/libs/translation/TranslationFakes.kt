// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation

import me.kafuuneko.rpclient.libs.translation.model.TranslationMode

/**
 * Stand-ins bound until the translation tracks merge, and reused by their tests. They never touch
 * the network.
 */

/** Translates by tagging the text, for example "[es] Hello", and detects nothing. */
class FakeTranslationClient : TranslationClient {
    override suspend fun translate(texts: List<String>, source: String?, target: String): List<String> =
        texts.map { "[$target] $it" }

    override suspend fun detect(text: String): String? = null
}

/** Builds [FakeTranslationClient]s. */
class FakeTranslationClientFactory : TranslationClientFactory {
    override fun create(config: TranslationProviderConfig): TranslationClient = FakeTranslationClient()
}

/** Detects nothing, like a device below API 29. */
class NoLanguageDetector : LanguageDetector {
    override suspend fun detect(text: String): String? = null
}

/** Translation turned off: shows and sends everything as is. */
class NoopChatTranslator : ChatTranslator {
    override suspend fun resolve(session: TranslationSessionKey, recent: List<TranslatableMessage>): TranslationState =
        TranslationState(mode = TranslationMode.Off, active = false, conversationLanguage = null, userLanguage = "")

    override suspend fun displayTexts(session: TranslationSessionKey, messages: List<TranslatableMessage>): Map<Long, String> =
        emptyMap()

    override suspend fun outgoing(session: TranslationSessionKey, input: String, names: List<String>): OutgoingTranslation =
        OutgoingTranslation(content = input, displayText = null)

    override suspend fun rememberOutgoing(session: TranslationSessionKey, messageId: Long, content: String, displayText: String) = Unit

    override suspend fun setMode(session: TranslationSessionKey, mode: TranslationMode) = Unit
}
