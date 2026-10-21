// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.provider

import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.translation.TranslationFailure
import me.kafuuneko.rpclient.libs.translation.model.TranslationProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibreTranslateClientTest {
    private fun client(http: CannedHttp, apiKey: String = "") =
        LibreTranslateClient(http.client, config(TranslationProviderType.LibreTranslate, baseUrl = "https://lt.example.org/", apiKey = apiKey))

    @Test
    fun `translate sends every text in one request and keeps the order`() = runBlocking {
        val http = CannedHttp(body = """{"translatedText":["Hola ⟦0⟧","Adiós"]}""")
        val result = client(http, apiKey = "secret").translate(listOf("Hello ⟦0⟧", "Bye"), source = null, target = "es")

        assertEquals(listOf("Hola ⟦0⟧", "Adiós"), result)
        assertEquals("https://lt.example.org/translate", http.lastRequest.url.toString())
        val json = http.lastJson().asJsonObject
        assertEquals(2, json["q"].asJsonArray.size())
        assertEquals("auto", json["source"].asString)
        assertEquals("es", json["target"].asString)
        assertEquals("text", json["format"].asString)
        assertEquals("secret", json["api_key"].asString)
    }

    @Test
    fun `no api key is sent when none is configured and tags are reduced to the primary subtag`() = runBlocking {
        val http = CannedHttp(body = """{"translatedText":["Olá"]}""")
        client(http).translate(listOf("Hi"), source = "EN", target = "pt-BR")

        val json = http.lastJson().asJsonObject
        assertFalse(json.has("api_key"))
        assertEquals("en", json["source"].asString)
        assertEquals("pt", json["target"].asString)
    }

    @Test
    fun `a single translatedText string is accepted for one text`() {
        val parsed = LibreTranslateClient.parseTranslateReply(TranslationHttpReply(200, """{"translatedText":"Hola"}"""))
        assertEquals(listOf("Hola"), parsed)
    }

    @Test
    fun `a reply with a different count of texts is invalid`() {
        val http = CannedHttp(body = """{"translatedText":["Hola"]}""")
        assertFailure(TranslationFailure.Invalid) { client(http).translate(listOf("a", "b"), null, "es") }
    }

    @Test
    fun `detect picks the most confident language`() = runBlocking {
        val http = CannedHttp(body = """[{"language":"fr","confidence":12.0},{"language":"es","confidence":91.5}]""")
        assertEquals("es", client(http).detect("Hola"))
        assertTrue(http.lastRequest.url.toString().endsWith("/detect"))
    }

    @Test
    fun `detect returns null for an empty list`() {
        assertNull(LibreTranslateClient.parseDetectReply(TranslationHttpReply(200, "[]")))
    }

    @Test
    fun `errors map to failures`() {
        assertEquals(TranslationFailure.Unsupported, LibreTranslateClient.failureFor(TranslationHttpReply(400, """{"error":"xx is not supported"}""")))
        assertEquals(TranslationFailure.Auth, LibreTranslateClient.failureFor(TranslationHttpReply(400, """{"error":"Invalid API key"}""")))
        assertEquals(TranslationFailure.Auth, LibreTranslateClient.failureFor(TranslationHttpReply(403, """{"error":"Banned"}""")))
        assertEquals(TranslationFailure.Quota, LibreTranslateClient.failureFor(TranslationHttpReply(429, "")))
        assertEquals(TranslationFailure.Invalid, LibreTranslateClient.failureFor(TranslationHttpReply(400, "not json")))
        assertEquals(TranslationFailure.Network, LibreTranslateClient.failureFor(TranslationHttpReply(502, "")))
        assertEquals(TranslationFailure.Invalid, LibreTranslateClient.failureFor(TranslationHttpReply(301, "")))
    }

    @Test
    fun `an http error is thrown as a translation failure`() {
        val http = CannedHttp(code = 429, body = """{"error":"Slowdown"}""")
        assertFailure(TranslationFailure.Quota) { client(http).translate(listOf("Hi"), null, "es") }
    }

    @Test
    fun `a malformed base url is invalid`() {
        val http = CannedHttp()
        val client = LibreTranslateClient(http.client, config(TranslationProviderType.LibreTranslate, baseUrl = "not a url"))
        assertFailure(TranslationFailure.Invalid) { client.translate(listOf("Hi"), null, "es") }
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `custom headers are applied and the client hides its key`() = runBlocking {
        val http = CannedHttp(body = """{"translatedText":["Hola"]}""")
        val client = LibreTranslateClient(
            http.client,
            config(TranslationProviderType.LibreTranslate, baseUrl = "https://lt.example.org", apiKey = "secret", customHeadersJson = """{"X-Proxy":"p"}""")
        )
        client.translate(listOf("Hi"), null, "es")
        assertEquals("p", http.lastRequest.header("X-Proxy"))
        assertFalse(client.toString().contains("secret"))
    }
}
