// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.provider

import java.io.IOException
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.llm.LLMHttpStatusException
import me.kafuuneko.rpclient.libs.llm.LLMProviderRequestException
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.translation.TranslationFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmTranslationClientTest {
    private class RecordingGateway(private val reply: (LLMGenerationRequest) -> String) : LlmTranslationGateway {
        val calls = mutableListOf<Pair<Long, LLMGenerationRequest>>()
        override suspend fun generate(llmProviderId: Long, request: LLMGenerationRequest): String {
            calls += llmProviderId to request
            return reply(request)
        }
    }

    @Test
    fun `each text is one request with the text alone as the user message`() = runBlocking {
        val gateway = RecordingGateway { "[es] " + it.messages.last().content }
        val result = LlmTranslationClient(gateway, 3L).translate(listOf("Hello ⟦0⟧", "Bye"), source = "en", target = "es")

        assertEquals(listOf("[es] Hello ⟦0⟧", "[es] Bye"), result)
        assertEquals(2, gateway.calls.size)
        val (providerId, request) = gateway.calls.first()
        assertEquals(3L, providerId)
        assertEquals(LLMMessageRole.System, request.messages.first().role)
        assertEquals(LLMMessageRole.User, request.messages.last().role)
        assertEquals("Hello ⟦0⟧", request.messages.last().content)
        val system = request.messages.first().content
        assertTrue(system.contains("English (en)"))
        assertTrue(system.contains("Spanish (es)"))
        assertTrue(system.contains("<x id=\"N\"/>"))
    }

    @Test
    fun `blank texts are kept without a request`() = runBlocking {
        val gateway = RecordingGateway { "x" }
        assertEquals(listOf(" "), LlmTranslationClient(gateway, 3L).translate(listOf(" "), null, "es"))
        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun `a reasoning block is removed from the reply`() {
        assertEquals("Hola", LlmTranslationClient.cleanReply("<think>\nplan\n</think>\n\nHola\n"))
        assertNull(LlmTranslationClient.cleanReply("<think>only</think>  "))
    }

    @Test
    fun `detection accepts only a bare code`() {
        assertEquals("es", LlmTranslationClient.parseLanguageCode("es"))
        assertEquals("pt", LlmTranslationClient.parseLanguageCode("`pt-BR`."))
        assertNull(LlmTranslationClient.parseLanguageCode("The language is Spanish"))
        assertNull(LlmTranslationClient.parseLanguageCode("und"))
    }

    @Test
    fun `a provider without an ai provider is invalid`() {
        assertFailure(TranslationFailure.Invalid) { LlmTranslationClient(RecordingGateway { "x" }, null).translate(listOf("Hi"), null, "es") }
    }

    @Test
    fun `an empty reply is invalid`() {
        assertFailure(TranslationFailure.Invalid) { LlmTranslationClient(RecordingGateway { "  " }, 1L).translate(listOf("Hi"), null, "es") }
    }

    @Test
    fun `ai provider errors map to translation failures`() {
        assertEquals(TranslationFailure.Auth, LlmTranslationClient.failureFor(LLMProviderRequestException("p", LLMHttpStatusException(401))))
        assertEquals(TranslationFailure.Quota, LlmTranslationClient.failureFor(LLMHttpStatusException(429)))
        assertEquals(TranslationFailure.Network, LlmTranslationClient.failureFor(IOException("offline")))
        assertEquals(TranslationFailure.Network, LlmTranslationClient.failureFor(LLMHttpStatusException(503)))
        assertEquals(TranslationFailure.Invalid, LlmTranslationClient.failureFor(LLMHttpStatusException(404)))
        assertFailure(TranslationFailure.Network) {
            LlmTranslationClient({ _, _ -> throw IOException("offline") }, 1L).translate(listOf("Hi"), null, "es")
        }
    }
}
