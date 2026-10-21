// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.chat.translation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import me.kafuuneko.rpclient.libs.translation.ChatTranslator
import me.kafuuneko.rpclient.libs.translation.OutgoingTranslation
import me.kafuuneko.rpclient.libs.translation.TranslatableMessage
import me.kafuuneko.rpclient.libs.translation.TranslationFailure
import me.kafuuneko.rpclient.libs.translation.TranslationSessionKey
import me.kafuuneko.rpclient.libs.translation.TranslationState
import me.kafuuneko.rpclient.libs.translation.model.TranslationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chat screens' translation glue against a translator that "translates" by tagging, so the
 * tests check what is shown, what is stored and when the translator is called at all.
 */
class ChatTranslationControllerTest {

    /** Tags display texts with "[es] " and outgoing content with "[en] "; records every call. */
    private class TaggingTranslator(
        var active: Boolean = true,
        var outgoingFailure: TranslationFailure? = null
    ) : ChatTranslator {
        var resolveCalls = 0
        val displayRequests = mutableListOf<List<Long>>()
        var outgoingCalls = 0
        val remembered = mutableListOf<Triple<Long, String, String>>()
        var mode = TranslationMode.Auto

        override suspend fun resolve(session: TranslationSessionKey, recent: List<TranslatableMessage>): TranslationState {
            resolveCalls++
            return TranslationState(mode = mode, active = active, conversationLanguage = "en", userLanguage = "es")
        }

        override suspend fun displayTexts(session: TranslationSessionKey, messages: List<TranslatableMessage>): Map<Long, String> {
            displayRequests += messages.map { it.id }
            return messages.associate { it.id to "[es] ${it.content}" }
        }

        override suspend fun outgoing(session: TranslationSessionKey, input: String, names: List<String>): OutgoingTranslation {
            outgoingCalls++
            val failure = outgoingFailure
            if (failure != null) return OutgoingTranslation(content = input, displayText = null, failure = failure)
            return OutgoingTranslation(content = "[en] $input", displayText = input)
        }

        override suspend fun rememberOutgoing(session: TranslationSessionKey, messageId: Long, content: String, displayText: String) {
            remembered += Triple(messageId, content, displayText)
        }

        override suspend fun setMode(session: TranslationSessionKey, mode: TranslationMode) {
            this.mode = mode
        }
    }

    private fun character(id: Long, content: String) = TranslatableMessage(id, content, fromUser = false)
    private fun user(id: Long, content: String) = TranslatableMessage(id, content, fromUser = true)

    /** Runs [block] with a controller whose background syncs finish before each [settle]. */
    private fun withController(
        translator: TaggingTranslator,
        enabled: Boolean = true,
        block: suspend Scope.() -> Unit
    ) = runBlocking {
        val job = Job()
        val scope = CoroutineScope(coroutineContext + job)
        var changes = 0
        val controller = ChatTranslationController(
            translator = translator,
            scope = scope,
            isEnabled = { enabled },
            ioDispatcher = Dispatchers.Unconfined
        ) { changes++ }
        controller.attach(TranslationSessionKey.Chat(1L))
        Scope(controller, job) { changes }.block()
        job.cancel()
    }

    private class Scope(val controller: ChatTranslationController, private val job: Job, val changes: () -> Int) {
        /** Lets the queued syncs run to completion. */
        suspend fun settle() {
            repeat(5) { yield() }
            job.children.forEach { it.join() }
        }
    }

    @Test
    fun offCallsNothingAndShowsNothing() {
        val translator = TaggingTranslator()
        withController(translator, enabled = false) {
            controller.onMessagesLoaded(listOf(character(1, "Hello")))
            controller.onResume()
            settle()
            val outgoing = controller.prepareOutgoing("Hola", emptyList())

            assertEquals(0, translator.resolveCalls)
            assertTrue(translator.displayRequests.isEmpty())
            assertEquals(0, translator.outgoingCalls)
            assertNull(controller.translationFor(1))
            assertEquals(ChatTranslationUiState(), controller.uiState())
            assertEquals(ChatTranslationController.Outgoing.Ready("Hola", null), outgoing)
            assertEquals(0, changes())
        }
    }

    @Test
    fun activeChatShowsDisplayTextsAndTheBanner() {
        val translator = TaggingTranslator()
        withController(translator) {
            controller.onMessagesLoaded(listOf(character(1, "Hello"), user(2, "Hi")))
            settle()

            assertEquals(1, translator.resolveCalls)
            assertEquals("[es] Hello", controller.translationFor(1)?.displayText)
            assertNull(controller.translationFor(1, isStreaming = true))
            val state = controller.uiState()
            assertTrue(state.active)
            assertTrue(state.available)
            assertEquals("en", state.conversationLanguage)
        }
    }

    @Test
    fun inactiveChatNeverAsksForDisplayTexts() {
        val translator = TaggingTranslator(active = false)
        withController(translator) {
            controller.onMessagesLoaded(listOf(character(1, "Hola")))
            settle()

            assertEquals(1, translator.resolveCalls)
            assertTrue(translator.displayRequests.isEmpty())
            assertNull(controller.translationFor(1))
            assertFalse(controller.uiState().active)
            assertTrue(controller.uiState().available)
        }
    }

    @Test
    fun editedContentIsStaleUntilTranslatedAgainAndOnlyItIsRequested() {
        val translator = TaggingTranslator()
        withController(translator) {
            controller.onMessagesLoaded(listOf(character(1, "Hello"), character(2, "Bye")))
            settle()
            translator.displayRequests.clear()

            controller.onMessagesLoaded(listOf(character(1, "Hello there"), character(2, "Bye")))
            assertNull(controller.translationFor(1))
            settle()

            assertEquals(listOf(listOf(1L)), translator.displayRequests)
            assertEquals("[es] Hello there", controller.translationFor(1)?.displayText)
            assertEquals("[es] Bye", controller.translationFor(2)?.displayText)
        }
    }

    @Test
    fun aCompletedCharacterMessageResolvesAgainButAUserMessageDoesNot() {
        val translator = TaggingTranslator()
        withController(translator) {
            controller.onMessagesLoaded(listOf(character(1, "Hello")))
            settle()
            controller.onMessagesLoaded(listOf(character(1, "Hello"), user(2, "Hi")))
            settle()
            assertEquals(1, translator.resolveCalls)

            controller.onMessagesLoaded(listOf(character(1, "Hello"), user(2, "Hi"), character(3, "How are you?")))
            settle()
            assertEquals(2, translator.resolveCalls)

            // The same window again is not a change: no new work.
            val requests = translator.displayRequests.size
            controller.onMessagesLoaded(listOf(character(1, "Hello"), user(2, "Hi"), character(3, "How are you?")))
            settle()
            assertEquals(2, translator.resolveCalls)
            assertEquals(requests, translator.displayRequests.size)
        }
    }

    @Test
    fun olderPagesAreTranslatedWithoutResolvingAgain() {
        val translator = TaggingTranslator()
        withController(translator) {
            controller.onMessagesLoaded(listOf(character(5, "Latest")))
            settle()
            controller.onMessagesLoaded(listOf(character(1, "Oldest")), older = true)
            settle()

            assertEquals(1, translator.resolveCalls)
            assertEquals("[es] Oldest", controller.translationFor(1)?.displayText)
            assertEquals("[es] Latest", controller.translationFor(5)?.displayText)
        }
    }

    @Test
    fun sentMessageStoresTranslatedContentAndShowsWhatWasTyped() {
        val translator = TaggingTranslator()
        withController(translator) {
            controller.onMessagesLoaded(listOf(character(1, "Hello")))
            settle()

            val outgoing = controller.prepareOutgoing("Hola", listOf("Alice", "Bob"))
            assertEquals(ChatTranslationController.Outgoing.Ready("[en] Hola", "Hola"), outgoing)
            controller.rememberOutgoing(2, "[en] Hola", "Hola")
            controller.onMessagesLoaded(listOf(character(1, "Hello"), user(2, "[en] Hola")))
            settle()

            assertEquals(listOf(Triple(2L, "[en] Hola", "Hola")), translator.remembered)
            assertEquals("Hola", controller.translationFor(2)?.displayText)
            assertEquals("Hola", controller.editableDisplayText(2, "[en] Hola"))
            assertNull(controller.editableDisplayText(1, "Hello"))
        }
    }

    @Test
    fun slashCommandsAreNeverTranslated() {
        val translator = TaggingTranslator()
        withController(translator) {
            controller.onMessagesLoaded(listOf(character(1, "Hello")))
            settle()

            val outgoing = controller.prepareOutgoing("/sys hello", emptyList())

            assertEquals(ChatTranslationController.Outgoing.Ready("/sys hello", null), outgoing)
            assertEquals(0, translator.outgoingCalls)
        }
    }

    @Test
    fun failedTranslationNeverBlocksSendingTheSameTextAgain() {
        val translator = TaggingTranslator(outgoingFailure = TranslationFailure.Network)
        withController(translator) {
            controller.onMessagesLoaded(listOf(character(1, "Hello")))
            settle()

            val first = controller.prepareOutgoing("Hola", emptyList())
            assertEquals(ChatTranslationController.Outgoing.Failed(TranslationFailure.Network), first)
            assertEquals(TranslationFailure.Network, controller.uiState().outgoingFailure)

            val second = controller.prepareOutgoing("Hola", emptyList())
            assertEquals(ChatTranslationController.Outgoing.Ready("Hola", null), second)
            assertEquals(1, translator.outgoingCalls)
            assertNull(controller.uiState().outgoingFailure)
        }
    }

    @Test
    fun changingTheDraftDropsTheFailureNotice() {
        val translator = TaggingTranslator(outgoingFailure = TranslationFailure.Quota)
        withController(translator) {
            controller.onMessagesLoaded(listOf(character(1, "Hello")))
            settle()
            controller.prepareOutgoing("Hola", emptyList())

            assertFalse(controller.onDraftChanged("Hola "))
            assertTrue(controller.onDraftChanged("Hola amigo"))
            assertNull(controller.uiState().outgoingFailure)
            // The new text is translated again rather than sent as written.
            assertTrue(controller.translatesOutgoing("Hola amigo"))
        }
    }

    @Test
    fun togglesSwitchBetweenTranslationAndOriginal() {
        val translator = TaggingTranslator()
        withController(translator) {
            controller.onMessagesLoaded(listOf(character(1, "Hello"), character(2, "Bye")))
            settle()

            controller.toggleOriginal(1)
            assertTrue(controller.translationFor(1)!!.showingOriginal)
            assertFalse(controller.translationFor(2)!!.showingOriginal)

            controller.toggleShowAllOriginal()
            assertTrue(controller.uiState().showOriginal)
            assertTrue(controller.translationFor(1)!!.showingOriginal)
            assertTrue(controller.translationFor(2)!!.showingOriginal)

            controller.toggleOriginal(2)
            assertFalse(controller.translationFor(2)!!.showingOriginal)
        }
    }

    @Test
    fun turningTheChatOffDropsItsTranslations() {
        val translator = TaggingTranslator()
        withController(translator) {
            controller.onMessagesLoaded(listOf(character(1, "Hello")))
            settle()
            assertTrue(controller.translationFor(1) != null)

            translator.active = false
            controller.setMode(TranslationMode.Off)
            settle()

            assertEquals(TranslationMode.Off, translator.mode)
            assertNull(controller.translationFor(1))
            assertFalse(controller.uiState().active)
            assertEquals(TranslationMode.Off, controller.uiState().mode)
        }
    }
}
