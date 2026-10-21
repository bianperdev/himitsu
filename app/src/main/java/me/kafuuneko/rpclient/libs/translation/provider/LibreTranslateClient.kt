// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.provider

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.translation.TranslationClient
import me.kafuuneko.rpclient.libs.translation.TranslationException
import me.kafuuneko.rpclient.libs.translation.TranslationFailure
import me.kafuuneko.rpclient.libs.translation.TranslationProviderConfig
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Client of a LibreTranslate server (`POST /translate`, `POST /detect`).
 *
 * - All texts go in one request as a `q` array; the server answers with an array in the same order.
 * - `format=text` keeps the numbered placeholder tokens as plain text.
 * - The optional API key travels in the JSON body, as the LibreTranslate API defines it.
 */
internal class LibreTranslateClient(
    private val mOkHttpClient: OkHttpClient,
    private val mConfig: TranslationProviderConfig
) : TranslationClient {

    override suspend fun translate(texts: List<String>, source: String?, target: String): List<String> {
        if (texts.isEmpty()) return emptyList()
        val reply = mOkHttpClient.sendTranslationRequest(buildTranslateRequest(texts, source, target))
        if (!reply.isSuccessful) throw TranslationException(failureFor(reply))
        return parseTranslateReply(reply).requireSameSizeAs(texts)
    }

    override suspend fun detect(text: String): String? {
        if (text.isBlank()) return null
        val reply = mOkHttpClient.sendTranslationRequest(buildDetectRequest(text))
        if (!reply.isSuccessful) throw TranslationException(failureFor(reply))
        return parseDetectReply(reply)
    }

    /** Request of `/translate`; `source` is "auto" when the server should detect it. */
    fun buildTranslateRequest(texts: List<String>, source: String?, target: String): Request {
        val body = JsonObject().apply {
            add("q", JsonArray().also { array -> texts.forEach(array::add) })
            addProperty("source", primaryLanguageTag(source) ?: "auto")
            addProperty("target", primaryLanguageTag(target) ?: throw TranslationException(TranslationFailure.Unsupported))
            addProperty("format", "text")
            addApiKey()
        }
        return post("translate", body)
    }

    /** Request of `/detect`. */
    fun buildDetectRequest(text: String): Request {
        val body = JsonObject().apply {
            addProperty("q", text)
            addApiKey()
        }
        return post("detect", body)
    }

    private fun JsonObject.addApiKey() {
        if (mConfig.apiKey.isNotBlank()) addProperty("api_key", mConfig.apiKey.trim())
    }

    private fun post(path: String, body: JsonObject): Request = Request.Builder()
        .url(translationEndpoint(mConfig.baseUrl, path))
        .applyCustomHeaders(mConfig.customHeadersJson)
        .post(body.toJsonRequestBody())
        .build()

    override fun toString(): String = "LibreTranslateClient(id=${mConfig.id}, [REDACTED])"

    companion object {
        /** `translatedText` is an array for an array `q`; a single string is accepted for one text. */
        fun parseTranslateReply(reply: TranslationHttpReply): List<String> {
            val json = reply.json().takeIf { it.isJsonObject }?.asJsonObject
                ?: throw TranslationException(TranslationFailure.Invalid)
            val translated = json.get("translatedText") ?: throw TranslationException(TranslationFailure.Invalid)
            return when {
                translated.isJsonArray -> translated.asJsonArray.map { element ->
                    if (element.isJsonPrimitive) element.asString else throw TranslationException(TranslationFailure.Invalid)
                }
                translated.isJsonPrimitive -> listOf(translated.asString)
                else -> throw TranslationException(TranslationFailure.Invalid)
            }
        }

        /** The most confident language of a `/detect` reply (`[{language, confidence}]`). */
        fun parseDetectReply(reply: TranslationHttpReply): String? {
            val json = reply.json().takeIf { it.isJsonArray }?.asJsonArray
                ?: throw TranslationException(TranslationFailure.Invalid)
            val best = json.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
                .maxByOrNull { candidate -> candidate.get("confidence")?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0 }
            return primaryLanguageTag(best?.stringOrNull("language"))
        }

        /**
         * LibreTranslate reports most refusals as 400 with an `error` message: an unknown
         * language says "not supported", a missing or invalid key mentions the API key.
         */
        fun failureFor(reply: TranslationHttpReply): TranslationFailure {
            val message = reply.jsonObjectOrNull()?.stringOrNull("error")?.lowercase().orEmpty()
            return when {
                reply.code == 400 && "not supported" in message -> TranslationFailure.Unsupported
                reply.code == 400 && ("api key" in message || "api_key" in message) -> TranslationFailure.Auth
                else -> failureForStatus(reply.code)
            }
        }
    }
}
