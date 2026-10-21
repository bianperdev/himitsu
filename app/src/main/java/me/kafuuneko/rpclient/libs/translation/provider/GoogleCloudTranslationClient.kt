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
 * Client of Google Cloud Translation v2 (Basic) with an API key.
 *
 * - The key goes only in the `X-goog-api-key` header; Google also accepts `?key=`, which would
 *   put it in URLs that proxies and server logs keep, so that form is never used.
 * - `format=text` returns plain text instead of HTML-escaped text.
 * - Requests are JSON bodies to `/language/translate/v2` and `/language/translate/v2/detect`.
 */
internal class GoogleCloudTranslationClient(
    private val mOkHttpClient: OkHttpClient,
    private val mConfig: TranslationProviderConfig
) : TranslationClient {

    override suspend fun translate(texts: List<String>, source: String?, target: String): List<String> {
        if (texts.isEmpty()) return emptyList()
        val reply = send(buildTranslateRequest(texts, source, target))
        return parseTranslateReply(reply).requireSameSizeAs(texts)
    }

    override suspend fun detect(text: String): String? {
        if (text.isBlank()) return null
        return parseDetectReply(send(buildDetectRequest(text)))
    }

    private suspend fun send(request: Request): TranslationHttpReply {
        if (mConfig.apiKey.isBlank()) throw TranslationException(TranslationFailure.Auth)
        val reply = mOkHttpClient.sendTranslationRequest(request)
        if (!reply.isSuccessful) throw TranslationException(failureFor(reply))
        return reply
    }

    /** Request of `/language/translate/v2`; no `source` lets Google detect it. */
    fun buildTranslateRequest(texts: List<String>, source: String?, target: String): Request {
        val body = JsonObject().apply {
            add("q", JsonArray().also { array -> texts.forEach(array::add) })
            addProperty("target", primaryLanguageTag(target) ?: throw TranslationException(TranslationFailure.Unsupported))
            primaryLanguageTag(source)?.let { addProperty("source", it) }
            addProperty("format", "text")
        }
        return post("language/translate/v2", body)
    }

    /** Request of `/language/translate/v2/detect`. */
    fun buildDetectRequest(text: String): Request {
        val body = JsonObject().apply { add("q", JsonArray().also { it.add(text) }) }
        return post("language/translate/v2/detect", body)
    }

    private fun post(path: String, body: JsonObject): Request = Request.Builder()
        .url(translationEndpoint(mConfig.baseUrl.trim().ifEmpty { DEFAULT_ENDPOINT }, path))
        .applyCustomHeaders(mConfig.customHeadersJson)
        .header("X-goog-api-key", mConfig.apiKey.trim())
        .post(body.toJsonRequestBody())
        .build()

    override fun toString(): String = "GoogleCloudTranslationClient(id=${mConfig.id}, [REDACTED])"

    companion object {
        const val DEFAULT_ENDPOINT = "https://translation.googleapis.com"

        private val QUOTA_REASONS = setOf("dailyLimitExceeded", "userRateLimitExceeded", "rateLimitExceeded", "quotaExceeded")
        private val AUTH_REASONS = setOf("keyInvalid", "keyExpired", "forbidden", "accessNotConfigured", "ipRefererBlocked")

        /** Reads `{"data": {"translations": [{"translatedText"}]}}`. */
        fun parseTranslateReply(reply: TranslationHttpReply): List<String> {
            val translations = reply.dataObject().get("translations")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: throw TranslationException(TranslationFailure.Invalid)
            return translations.map { element ->
                element.takeIf { it.isJsonObject }?.asJsonObject?.stringOrNull("translatedText")
                    ?: throw TranslationException(TranslationFailure.Invalid)
            }
        }

        /** Reads `{"data": {"detections": [[{"language", "confidence"}]]}}`; "und" means unknown. */
        fun parseDetectReply(reply: TranslationHttpReply): String? {
            val detections = reply.dataObject().get("detections")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: throw TranslationException(TranslationFailure.Invalid)
            val candidates = detections.firstOrNull()?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
            val best = candidates.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
                .maxByOrNull { candidate -> candidate.get("confidence")?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0 }
            return primaryLanguageTag(best?.stringOrNull("language"))?.takeUnless { it == "und" }
        }

        /**
         * Google answers a bad key with 400 and the reason "keyInvalid", and quota problems
         * with 403 or 429; the reason in `error.errors[]` tells them apart.
         */
        fun failureFor(reply: TranslationHttpReply): TranslationFailure {
            val error = reply.jsonObjectOrNull()?.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
            val reasons = error?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray.orEmpty()
                .mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject?.stringOrNull("reason") }
            val message = error?.stringOrNull("message")?.lowercase().orEmpty()
            return when {
                reasons.any { it in QUOTA_REASONS } -> TranslationFailure.Quota
                reasons.any { it in AUTH_REASONS } || "api key not valid" in message -> TranslationFailure.Auth
                reply.code == 400 && ("invalid value" in message || "not supported" in message) -> TranslationFailure.Unsupported
                else -> failureForStatus(reply.code)
            }
        }

        private fun TranslationHttpReply.dataObject(): JsonObject =
            json().takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: throw TranslationException(TranslationFailure.Invalid)

        private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray()
    }
}
