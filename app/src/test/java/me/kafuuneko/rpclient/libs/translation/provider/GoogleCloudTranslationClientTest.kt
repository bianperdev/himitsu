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

class GoogleCloudTranslationClientTest {
    private fun client(http: CannedHttp, apiKey: String = "gkey", baseUrl: String = "") =
        GoogleCloudTranslationClient(http.client, config(TranslationProviderType.GoogleCloud, baseUrl = baseUrl, apiKey = apiKey))

    @Test
    fun `translate puts the key in the header and never in the url`() = runBlocking {
        val http = CannedHttp(body = """{"data":{"translations":[{"translatedText":"Hola"},{"translatedText":"Adiós"}]}}""")
        val result = client(http).translate(listOf("Hello", "Bye"), source = null, target = "es")

        assertEquals(listOf("Hola", "Adiós"), result)
        val request = http.lastRequest
        assertEquals("https://translation.googleapis.com/language/translate/v2", request.url.toString())
        assertNull(request.url.queryParameter("key"))
        assertEquals("gkey", request.header("X-goog-api-key"))
        val json = http.lastJson().asJsonObject
        assertEquals("text", json["format"].asString)
        assertEquals("es", json["target"].asString)
        assertFalse(json.has("source"))
        assertFalse(json.toString().contains("gkey"))
    }

    @Test
    fun `a configured base url replaces the default endpoint`() = runBlocking {
        val http = CannedHttp(body = """{"data":{"translations":[{"translatedText":"Hola"}]}}""")
        client(http, baseUrl = "https://proxy.example.org/").translate(listOf("Hello"), source = "en", target = "es")
        assertEquals("https://proxy.example.org/language/translate/v2", http.lastRequest.url.toString())
        assertEquals("en", http.lastJson().asJsonObject["source"].asString)
    }

    @Test
    fun `detect reads the most confident detection and ignores und`() = runBlocking {
        val http = CannedHttp(body = """{"data":{"detections":[[{"language":"zh-CN","confidence":0.9},{"language":"ja","confidence":0.1}]]}}""")
        assertEquals("zh", client(http).detect("你好"))
        assertTrue(http.lastRequest.url.toString().endsWith("/language/translate/v2/detect"))
        assertNull(GoogleCloudTranslationClient.parseDetectReply(TranslationHttpReply(200, """{"data":{"detections":[[{"language":"und","confidence":1}]]}}""")))
    }

    @Test
    fun `errors map to failures from the reason`() {
        val badKey = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","errors":[{"reason":"badRequest"}]}}"""
        assertEquals(TranslationFailure.Auth, GoogleCloudTranslationClient.failureFor(TranslationHttpReply(400, badKey)))
        val quota = """{"error":{"code":403,"message":"Daily Limit Exceeded","errors":[{"reason":"dailyLimitExceeded"}]}}"""
        assertEquals(TranslationFailure.Quota, GoogleCloudTranslationClient.failureFor(TranslationHttpReply(403, quota)))
        val language = """{"error":{"code":400,"message":"Invalid Value","errors":[{"reason":"invalid"}]}}"""
        assertEquals(TranslationFailure.Unsupported, GoogleCloudTranslationClient.failureFor(TranslationHttpReply(400, language)))
        assertEquals(TranslationFailure.Auth, GoogleCloudTranslationClient.failureFor(TranslationHttpReply(403, "")))
        assertEquals(TranslationFailure.Network, GoogleCloudTranslationClient.failureFor(TranslationHttpReply(500, "")))
    }

    @Test
    fun `a missing key fails as auth without a request`() {
        val http = CannedHttp()
        assertFailure(TranslationFailure.Auth) { client(http, apiKey = "").detect("Hi") }
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `a reply without data is invalid`() {
        val http = CannedHttp(body = """{"translations":[]}""")
        assertFailure(TranslationFailure.Invalid) { client(http).translate(listOf("Hi"), null, "es") }
    }
}
