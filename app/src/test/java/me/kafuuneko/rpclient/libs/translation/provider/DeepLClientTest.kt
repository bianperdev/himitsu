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

class DeepLClientTest {
    private fun client(http: CannedHttp, apiKey: String = "key:fx", baseUrl: String = "") =
        DeepLClient(http.client, config(TranslationProviderType.DeepL, baseUrl = baseUrl, apiKey = apiKey))

    @Test
    fun `the endpoint follows the key suffix unless a base url is set`() {
        val http = CannedHttp()
        assertEquals(DeepLClient.FREE_ENDPOINT, client(http, apiKey = "abc:fx").endpointBase())
        assertEquals(DeepLClient.PRO_ENDPOINT, client(http, apiKey = "abc").endpointBase())
        assertEquals("https://proxy.example.org", client(http, apiKey = "abc:fx", baseUrl = "https://proxy.example.org").endpointBase())
    }

    @Test
    fun `translate sends the key only in the authorization header with xml tag handling`() = runBlocking {
        val http = CannedHttp(body = """{"translations":[{"detected_source_language":"EN","text":"Hola <x id=\"0\"/>"}]}""")
        val result = client(http, apiKey = "abc:fx").translate(listOf("Hello <x id=\"0\"/>"), source = "en", target = "es")

        assertEquals(listOf("Hola <x id=\"0\"/>"), result)
        val request = http.lastRequest
        assertEquals("https://api-free.deepl.com/v2/translate", request.url.toString())
        assertEquals("DeepL-Auth-Key abc:fx", request.header("Authorization"))
        val json = http.lastJson().asJsonObject
        assertEquals("ES", json["target_lang"].asString)
        assertEquals("EN", json["source_lang"].asString)
        assertEquals("xml", json["tag_handling"].asString)
        assertEquals("Hello <x id=\"0\"/>", json["text"].asJsonArray[0].asString)
        assertFalse(json.toString().contains("abc:fx"))
        assertFalse(request.url.toString().contains("abc"))
    }

    @Test
    fun `english and portuguese targets get a variant and no source lets deepl detect`() = runBlocking {
        val http = CannedHttp(body = """{"translations":[{"text":"Hi"}]}""")
        client(http).translate(listOf("Hola"), source = null, target = "en")
        val json = http.lastJson().asJsonObject
        assertEquals("EN-US", json["target_lang"].asString)
        assertFalse(json.has("source_lang"))
        assertEquals("PT-BR", DeepLClient.deepLTargetLanguage("pt"))
        assertEquals("JA", DeepLClient.deepLTargetLanguage("ja"))
    }

    @Test
    fun `detect reads the detected source language of a short excerpt`() = runBlocking {
        val http = CannedHttp(body = """{"translations":[{"detected_source_language":"DE","text":"Hello"}]}""")
        assertEquals("de", client(http).detect("Hallo ".repeat(100)))
        val sent = http.lastJson().asJsonObject["text"].asJsonArray[0].asString
        assertTrue(sent.length <= 200)
    }

    @Test
    fun `a missing key fails as auth without a request`() {
        val http = CannedHttp()
        assertFailure(TranslationFailure.Auth) { client(http, apiKey = " ").translate(listOf("Hi"), null, "es") }
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `errors map to failures`() {
        assertEquals(TranslationFailure.Auth, DeepLClient.failureFor(TranslationHttpReply(403, """{"message":"Forbidden"}""")))
        assertEquals(TranslationFailure.Quota, DeepLClient.failureFor(TranslationHttpReply(456, """{"message":"Quota exceeded"}""")))
        assertEquals(TranslationFailure.Quota, DeepLClient.failureFor(TranslationHttpReply(429, "")))
        assertEquals(TranslationFailure.Unsupported, DeepLClient.failureFor(TranslationHttpReply(400, """{"message":"Value for 'target_lang' not supported."}""")))
        assertEquals(TranslationFailure.Invalid, DeepLClient.failureFor(TranslationHttpReply(400, """{"message":"Bad request"}""")))
        assertEquals(TranslationFailure.Network, DeepLClient.failureFor(TranslationHttpReply(503, "")))
    }

    @Test
    fun `a reply without translations is invalid`() {
        val http = CannedHttp(body = """{"message":"ok"}""")
        assertFailure(TranslationFailure.Invalid) { client(http).translate(listOf("Hi"), null, "es") }
        assertNull(DeepLClient.parseTranslateReply(TranslationHttpReply(200, """{"translations":[{"text":"x"}]}""")).first().detectedSourceLanguage)
    }
}
