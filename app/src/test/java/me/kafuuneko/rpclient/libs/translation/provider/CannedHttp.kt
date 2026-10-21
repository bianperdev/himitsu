// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.provider

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.translation.TranslationException
import me.kafuuneko.rpclient.libs.translation.TranslationFailure
import me.kafuuneko.rpclient.libs.translation.TranslationProviderConfig
import me.kafuuneko.rpclient.libs.translation.model.TranslationProviderType
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

/** An OkHttp client that answers every call with a canned reply and records the requests. */
internal class CannedHttp(private val code: Int = 200, private val body: String = "{}") {
    val requests = mutableListOf<Request>()

    val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .addInterceptor(Interceptor { chain ->
            requests += chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("canned")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        })
        .build()

    val lastRequest: Request get() = requests.last()

    /** The JSON body of the last request. */
    fun lastJson(): JsonElement {
        val buffer = Buffer()
        lastRequest.body!!.writeTo(buffer)
        return JsonParser.parseString(buffer.readUtf8())
    }
}

internal fun config(
    type: TranslationProviderType,
    baseUrl: String = "",
    apiKey: String = "",
    llmProviderId: Long? = null,
    customHeadersJson: String = ""
) = TranslationProviderConfig(
    id = 7L,
    name = "Test",
    type = type,
    baseUrl = baseUrl,
    apiKey = apiKey,
    llmProviderId = llmProviderId,
    customHeadersJson = customHeadersJson
)

/** Runs [block] and asserts it fails with [expected]. */
internal fun assertFailure(expected: TranslationFailure, block: suspend () -> Unit) {
    try {
        runBlocking { block() }
        fail("Expected TranslationException($expected)")
    } catch (error: TranslationException) {
        assertEquals(expected, error.failure)
    }
}
