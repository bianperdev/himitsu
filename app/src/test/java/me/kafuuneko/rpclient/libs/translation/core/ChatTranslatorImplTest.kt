// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.core

import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.translation.FakeTranslationClientFactory
import me.kafuuneko.rpclient.libs.translation.TranslatableMessage
import me.kafuuneko.rpclient.libs.translation.TranslationException
import me.kafuuneko.rpclient.libs.translation.TranslationFailure
import me.kafuuneko.rpclient.libs.translation.TranslationSessionKey
import me.kafuuneko.rpclient.libs.translation.model.ChatMessageTranslation
import me.kafuuneko.rpclient.libs.translation.model.ChatTranslationSetting
import me.kafuuneko.rpclient.libs.translation.model.TranslationMode
import me.kafuuneko.rpclient.libs.translation.model.TranslationProvider
import me.kafuuneko.rpclient.libs.translation.model.TranslationProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTranslatorImplTest {
    private val providers = InMemoryTranslationProviderDao().apply {
        rows[1L] = TranslationProvider(id = 1L, name = "Libre", type = TranslationProviderType.LibreTranslate, baseUrl = "https://lt.example", createTime = 0L)
    }
    private val chatMessages = InMemoryChatMessageTranslationDao()
    private val groupMessages = InMemoryGroupChatMessageTranslationDao()
    private val chatSettings = InMemoryChatTranslationSettingDao()
    private val groupSettings = InMemoryGroupChatTranslationSettingDao()
    private val clients = RecordingClientFactory()
    private val detector = StubLanguageDetector("en")
    private val settings = FakeCoreSettings()
    private val chat = TranslationSessionKey.Chat(7L)

    private val translator = ChatTranslatorImpl(
        providerDao = providers,
        chatMessageDao = chatMessages,
        groupMessageDao = groupMessages,
        chatSettingDao = chatSettings,
        groupSettingDao = groupSettings,
        clientFactory = clients,
        detector = detector,
        protector = RegexTextProtector(),
        settings = settings,
        clock = { 42L }
    )

    private fun character(id: Long, content: String = "The rain kept falling over the quiet old town all night long.") =
        TranslatableMessage(id = id, content = content, fromUser = false, names = listOf("Alice"))

    private fun user(id: Long, content: String) = TranslatableMessage(id = id, content = content, fromUser = true)

    private fun storeLanguage(language: String, mode: TranslationMode = TranslationMode.Auto) {
        chatSettings.rows[chat.sessionId] = ChatTranslationSetting(chat.sessionId, mode, language)
    }

    // resolve

    @Test
    fun inactiveWhenMasterSwitchIsOffAndNothingIsDetected() = runBlocking {
        settings.enabled = false
        val state = translator.resolve(chat, listOf(character(1)))
        assertFalse(state.active)
        assertEquals("es", state.userLanguage)
        assertEquals(0, detector.calls)
        assertEquals(0, clients.detectCalls)
    }

    @Test
    fun inactiveWithoutAUsableProvider() = runBlocking {
        settings.defaultProviderId = null
        assertFalse(translator.resolve(chat, listOf(character(1))).active)
        settings.defaultProviderId = 99L
        assertFalse(translator.resolve(chat, listOf(character(1))).active)
        providers.rows[2L] = TranslationProvider(id = 2L, name = "Llm", type = TranslationProviderType.LlmProvider, createTime = 0L)
        settings.defaultProviderId = 2L
        assertFalse(translator.resolve(chat, listOf(character(1))).active)
    }

    @Test
    fun chatProviderOverridesTheDefault() = runBlocking {
        settings.defaultProviderId = null
        chatSettings.rows[chat.sessionId] = ChatTranslationSetting(chat.sessionId, providerId = 1L)
        assertTrue(translator.resolve(chat, listOf(character(1))).active)
    }

    @Test
    fun modeOffNeverTranslates() = runBlocking {
        translator.setMode(chat, TranslationMode.Off)
        val state = translator.resolve(chat, listOf(character(1)))
        assertEquals(TranslationMode.Off, state.mode)
        assertFalse(state.active)
        assertEquals(0, detector.calls)
    }

    @Test
    fun autoDetectsAnotherLanguageAndStoresIt() = runBlocking {
        val state = translator.resolve(chat, listOf(character(1), character(2)))
        assertEquals(TranslationMode.Auto, state.mode)
        assertTrue(state.active)
        assertEquals("en", state.conversationLanguage)
        assertEquals("en", chatSettings.rows.getValue(chat.sessionId).conversationLanguage)
        assertEquals(1, detector.calls)
    }

    @Test
    fun autoStaysInactiveWhenTheCharacterSpeaksTheUsersLanguage() = runBlocking {
        detector.answer = "es"
        val state = translator.resolve(chat, listOf(character(1)))
        assertFalse(state.active)
        assertEquals("es", state.conversationLanguage)
    }

    @Test
    fun autoIgnoresShortAndUserMessages() = runBlocking {
        val state = translator.resolve(chat, listOf(character(1, "*nods*"), character(2, "Hi!"), user(3, "A long message the user wrote in Spanish, actually.")))
        assertFalse(state.active)
        assertEquals(0, detector.calls)
        assertNull(state.conversationLanguage)
    }

    @Test
    fun modeOnUsesTheStoredLanguageWithoutDetecting() = runBlocking {
        storeLanguage("fr", TranslationMode.On)
        val state = translator.resolve(chat, listOf(character(1)))
        assertTrue(state.active)
        assertEquals("fr", state.conversationLanguage)
        assertEquals(0, detector.calls)
    }

    @Test
    fun modeOnDetectsWhenTheLanguageIsMissing() = runBlocking {
        translator.setMode(chat, TranslationMode.On)
        val state = translator.resolve(chat, listOf(character(1)))
        assertTrue(state.active)
        assertEquals("en", state.conversationLanguage)
    }

    @Test
    fun providerDetectsOnlyWhenTheDeviceCannot() = runBlocking {
        clients.detectAnswer = "de"
        assertEquals("en", translator.resolve(chat, listOf(character(1))).conversationLanguage)
        assertEquals(0, clients.detectCalls)

        detector.answer = null
        val other = TranslationSessionKey.Chat(8L)
        assertEquals("de", translator.resolve(other, listOf(character(1))).conversationLanguage)
        assertEquals(1, clients.detectCalls)
    }

    @Test
    fun providerDetectsOnlyWhileTheLanguageIsUnknown() = runBlocking {
        // Like API 26-28: the device never detects
        detector.answer = null
        clients.detectAnswer = "de"
        assertEquals("de", translator.resolve(chat, listOf(character(1))).conversationLanguage)
        assertEquals(1, clients.detectCalls)

        // Stored now: further resolves reuse it and send no sample
        clients.detectAnswer = "fr"
        val again = translator.resolve(chat, listOf(character(1), character(2)))
        assertEquals("de", again.conversationLanguage)
        assertTrue(again.active)
        assertEquals(1, clients.detectCalls)

        // The device still re-detects in Auto mode when it can
        detector.answer = "it"
        assertEquals("it", translator.resolve(chat, listOf(character(1))).conversationLanguage)
        assertEquals(1, clients.detectCalls)
    }

    @Test
    fun detectionSampleLeavesNamesOut() = runBlocking {
        detector.answer = null
        val captured = mutableListOf<String>()
        val capturing = ChatTranslatorImpl(
            providers, chatMessages, groupMessages, chatSettings, groupSettings, clients,
            detector = { text -> captured += text; null },
            protector = RegexTextProtector(),
            settings = settings
        )
        capturing.resolve(chat, listOf(character(1, "*Alice looks at the long road ahead and sighs softly.*")))
        assertEquals(1, captured.size)
        assertFalse(captured.single().contains("Alice"))
        assertFalse(captured.single().contains("*"))
    }

    // displayTexts

    @Test
    fun translatesCharacterMessagesAndCachesThem() = runBlocking {
        storeLanguage("en")
        val messages = listOf(character(1, "*Alice smiles* Good morning."), character(2, "How did you sleep?"))
        val first = translator.displayTexts(chat, messages)
        assertEquals("[es] *Alice smiles* Good morning.", first[1L])
        assertEquals("[es] How did you sleep?", first[2L])
        assertEquals(1, clients.translateCalls.size)
        val (sent, source, target) = clients.translateCalls.single()
        assertEquals("en", source)
        assertEquals("es", target)
        // Names and markers are sent as tokens
        assertFalse(sent[0].contains("Alice"))
        val row = chatMessages.rows.getValue(1L)
        assertEquals(ChatTranslatorImpl.sha256("*Alice smiles* Good morning."), row.contentHash)
        assertEquals("en", row.conversationLanguage)
        assertEquals("es", row.displayLanguage)
        assertEquals(1L, row.providerId)

        val second = translator.displayTexts(chat, messages)
        assertEquals(first, second)
        assertEquals(1, clients.translateCalls.size)
    }

    @Test
    fun staleRowsAreTranslatedAgain() = runBlocking {
        storeLanguage("en")
        translator.displayTexts(chat, listOf(character(1, "Old text")))
        val edited = translator.displayTexts(chat, listOf(character(1, "New text")))
        assertEquals("[es] New text", edited[1L])
        assertEquals(2, clients.translateCalls.size)
        assertEquals(ChatTranslatorImpl.sha256("New text"), chatMessages.rows.getValue(1L).contentHash)
    }

    @Test
    fun aNewUserLanguageTranslatesAgain() = runBlocking {
        storeLanguage("en")
        translator.displayTexts(chat, listOf(character(1, "Hello")))
        settings.language = "de"
        assertEquals("[de] Hello", translator.displayTexts(chat, listOf(character(1, "Hello")))[1L])
    }

    @Test
    fun lostTokensStoreNothingAndShowTheOriginal() = runBlocking {
        storeLanguage("en")
        clients.translateOverride = { texts -> texts.map { "sin marcas" } }
        val result = translator.displayTexts(chat, listOf(character(1, "*Alice smiles*")))
        assertTrue(result.isEmpty())
        assertTrue(chatMessages.rows.isEmpty())
    }

    @Test
    fun failuresNeverReachTheCaller() = runBlocking {
        storeLanguage("en")
        clients.translateOverride = { throw TranslationException(TranslationFailure.Network) }
        assertTrue(translator.displayTexts(chat, listOf(character(1))).isEmpty())
        clients.translateOverride = { texts -> texts.take(1) }
        assertTrue(translator.displayTexts(chat, listOf(character(1), character(2))).isEmpty())
    }

    @Test
    fun largeRequestsAreBatched() = runBlocking {
        storeLanguage("en")
        val messages = (1L..20L).map { character(it, "Line $it") }
        val result = translator.displayTexts(chat, messages)
        assertEquals(20, result.size)
        assertEquals(listOf(ChatTranslatorImpl.MAX_BATCH_TEXTS, 20 - ChatTranslatorImpl.MAX_BATCH_TEXTS), clients.translateCalls.map { it.first.size })
    }

    @Test
    fun nothingIsTranslatedWhileInactive() = runBlocking {
        storeLanguage("en", TranslationMode.Off)
        assertTrue(translator.displayTexts(chat, listOf(character(1))).isEmpty())
        settings.enabled = false
        storeLanguage("en")
        assertTrue(translator.displayTexts(chat, listOf(character(1))).isEmpty())
        assertTrue(clients.translateCalls.isEmpty())
    }

    @Test
    fun userMessagesShowWhatTheUserTypedWhileItMatches() = runBlocking {
        storeLanguage("en")
        translator.rememberOutgoing(chat, 5L, content = "Good morning", displayText = "Buenos días")
        val row = chatMessages.rows.getValue(5L)
        assertEquals("es", row.displayLanguage)
        assertEquals("en", row.conversationLanguage)
        assertEquals(42L, row.createTime)
        assertEquals("Buenos días", translator.displayTexts(chat, listOf(user(5L, "Good morning")))[5L])
        // Edited content: the typed text no longer matches
        assertTrue(translator.displayTexts(chat, listOf(user(5L, "Good evening"))).isEmpty())
        assertTrue(clients.translateCalls.isEmpty())
    }

    @Test
    fun groupChatsUseTheirOwnTables() = runBlocking {
        val group = TranslationSessionKey.GroupChat(7L)
        val result = translator.displayTexts(group, listOf(character(1, "The rain kept falling over the quiet old town all night long.")))
        assertEquals(1, result.size)
        assertEquals(1, groupMessages.rows.size)
        assertEquals("en", groupSettings.rows.getValue(7L).conversationLanguage)
        assertTrue(chatMessages.rows.isEmpty())
        assertTrue(chatSettings.rows.isEmpty())
    }

    @Test
    fun theGroundworkFakeFactoryWorks() = runBlocking {
        storeLanguage("en")
        val withFake = ChatTranslatorImpl(
            providers, chatMessages, groupMessages, chatSettings, groupSettings, FakeTranslationClientFactory(),
            detector, RegexTextProtector(), settings
        )
        assertEquals("[es] Hi there", withFake.displayTexts(chat, listOf(character(1, "Hi there")))[1L])
    }

    // outgoing

    @Test
    fun outgoingTranslatesIntoTheConversationLanguage() = runBlocking {
        storeLanguage("en")
        val result = translator.outgoing(chat, "*Sonrío* hola, Alice", names = listOf("Alice"))
        assertEquals("[en] *Sonrío* hola, Alice", result.content)
        assertEquals("*Sonrío* hola, Alice", result.displayText)
        assertNull(result.failure)
        val (_, source, target) = clients.translateCalls.single()
        assertEquals("es", source)
        assertEquals("en", target)
    }

    @Test
    fun outgoingFailureKeepsTheInput() = runBlocking {
        storeLanguage("en")
        clients.translateOverride = { throw TranslationException(TranslationFailure.Quota) }
        val failed = translator.outgoing(chat, "hola", emptyList())
        assertEquals("hola", failed.content)
        assertNull(failed.displayText)
        assertEquals(TranslationFailure.Quota, failed.failure)

        clients.translateOverride = { listOf("lost the token") }
        assertEquals(TranslationFailure.Invalid, translator.outgoing(chat, "*hola*", emptyList()).failure)

        clients.translateOverride = { throw java.io.IOException("offline") }
        assertEquals(TranslationFailure.Network, translator.outgoing(chat, "hola", emptyList()).failure)
    }

    @Test
    fun outgoingIsUnchangedWhenNotTranslating() = runBlocking {
        val unknownLanguage = translator.outgoing(chat, "hola", emptyList())
        assertEquals("hola", unknownLanguage.content)
        assertNull(unknownLanguage.failure)

        storeLanguage("en")
        settings.translateOwnMessages = false
        val ownOff = translator.outgoing(chat, "hola", emptyList())
        assertNull(ownOff.displayText)
        assertNull(ownOff.failure)

        settings.translateOwnMessages = true
        storeLanguage("es")
        assertNull(translator.outgoing(chat, "hola", emptyList()).displayText)
        assertTrue(clients.translateCalls.isEmpty())
    }

    // settings

    @Test
    fun setModeKeepsTheLanguageAndProvider() = runBlocking {
        chatSettings.rows[chat.sessionId] = ChatTranslationSetting(chat.sessionId, TranslationMode.Auto, "en", 1L)
        translator.setMode(chat, TranslationMode.On)
        assertEquals(ChatTranslationSetting(chat.sessionId, TranslationMode.On, "en", 1L), chatSettings.rows.getValue(chat.sessionId))
    }

    @Test
    fun hashIsLowerCaseHexSha256() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ChatTranslatorImpl.sha256(""))
    }

    @Test
    fun storedRowsAreReadThroughTheCache() = runBlocking {
        storeLanguage("en")
        chatMessages.rows[1L] = ChatMessageTranslation(1L, "Hola", "es", "en", ChatTranslatorImpl.sha256("Hello"), 1L, 0L)
        assertEquals("Hola", translator.displayTexts(chat, listOf(character(1, "Hello")))[1L])
        assertTrue(clients.translateCalls.isEmpty())
    }
}
