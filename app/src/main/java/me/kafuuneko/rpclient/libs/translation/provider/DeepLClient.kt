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
 * Client of the DeepL API (`POST /v2/translate`, JSON body).
 *
 * - The endpoint is the configured base URL, or else the free one for keys ending in ":fx" and
 *   the pro one otherwise.
 * - The key goes only in the `Authorization: DeepL-Auth-Key` header, never in the URL or body.
 * - `tag_handling=xml` makes DeepL keep the XML placeholder tokens; the client passes the texts
 *   through untouched and leaves restoring them to the text protector.
 * - DeepL has no detection endpoint: [detect] translates a short excerpt and reads the
 *   detected source language, so it sends the text to the same service a translation would.
 */
internal class DeepLClient(
    private val mOkHttpClient: OkHttpClient,
    private val mConfig: TranslationProviderConfig
) : TranslationClient {

    override suspend fun translate(texts: List<String>, source: String?, target: String): List<String> {
        if (texts.isEmpty()) return emptyList()
        val reply = send(buildTranslateRequest(texts, source, target))
        return parseTranslateReply(reply).map { it.text }.requireSameSizeAs(texts)
    }

    override suspend fun detect(text: String): String? {
        if (text.isBlank()) return null
        val excerpt = text.take(DETECT_EXCERPT_CHARS)
        val reply = send(buildTranslateRequest(listOf(excerpt), source = null, target = "en"))
        return primaryLanguageTag(parseTranslateReply(reply).firstOrNull()?.detectedSourceLanguage)
    }

    private suspend fun send(request: Request): TranslationHttpReply {
        if (mConfig.apiKey.isBlank()) throw TranslationException(TranslationFailure.Auth)
        val reply = mOkHttpClient.sendTranslationRequest(request)
        if (!reply.isSuccessful) throw TranslationException(failureFor(reply))
        return reply
    }

    /** Request of `/v2/translate`; no `source_lang` lets DeepL detect it. */
    fun buildTranslateRequest(texts: List<String>, source: String?, target: String): Request {
        val body = JsonObject().apply {
            add("text", JsonArray().also { array -> texts.forEach(array::add) })
            addProperty("target_lang", deepLTargetLanguage(target))
            primaryLanguageTag(source)?.let { addProperty("source_lang", it.uppercase()) }
            addProperty("tag_handling", "xml")
        }
        return Request.Builder()
            .url(translationEndpoint(endpointBase(), "v2/translate"))
            .applyCustomHeaders(mConfig.customHeadersJson)
            .header("Authorization", "DeepL-Auth-Key ${mConfig.apiKey.trim()}")
            .post(body.toJsonRequestBody())
            .build()
    }

    /** The configured base URL, or the free or pro endpoint chosen by the key. */
    fun endpointBase(): String = mConfig.baseUrl.trim().ifEmpty {
        if (mConfig.apiKey.trim().endsWith(FREE_KEY_SUFFIX)) FREE_ENDPOINT else PRO_ENDPOINT
    }

    override fun toString(): String = "DeepLClient(id=${mConfig.id}, [REDACTED])"

    /** One entry of a DeepL reply. */
    data class DeepLTranslation(val text: String, val detectedSourceLanguage: String?) {
        override fun toString(): String = "DeepLTranslation([REDACTED], detected=$detectedSourceLanguage)"
    }

    companion object {
        const val FREE_ENDPOINT = "https://api-free.deepl.com"
        const val PRO_ENDPOINT = "https://api.deepl.com"
        private const val FREE_KEY_SUFFIX = ":fx"

        /** Enough text to tell the language, without spending the character quota on a whole message. */
        private const val DETECT_EXCERPT_CHARS = 200

        /**
         * DeepL target code. English and Portuguese need a variant as targets; the app ships
         * Brazilian Portuguese, and US English is DeepL's own default.
         */
        fun deepLTargetLanguage(target: String): String {
            val tag = primaryLanguageTag(target) ?: throw TranslationException(TranslationFailure.Unsupported)
            return when (tag) {
                "en" -> "EN-US"
                "pt" -> "PT-BR"
                else -> tag.uppercase()
            }
        }

        /** Reads `{"translations": [{"text", "detected_source_language"}]}`. */
        fun parseTranslateReply(reply: TranslationHttpReply): List<DeepLTranslation> {
            val translations = reply.json().takeIf { it.isJsonObject }?.asJsonObject
                ?.get("translations")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: throw TranslationException(TranslationFailure.Invalid)
            return translations.map { element ->
                val item = element.takeIf { it.isJsonObject }?.asJsonObject
                    ?: throw TranslationException(TranslationFailure.Invalid)
                DeepLTranslation(
                    text = item.stringOrNull("text") ?: throw TranslationException(TranslationFailure.Invalid),
                    detectedSourceLanguage = item.stringOrNull("detected_source_language")
                )
            }
        }

        /**
         * DeepL statuses: 403 bad key, 456 quota exceeded, 429 too many requests; a 400 whose
         * message says "not supported" is an unknown language.
         */
        fun failureFor(reply: TranslationHttpReply): TranslationFailure {
            val message = reply.jsonObjectOrNull()?.stringOrNull("message")?.lowercase().orEmpty()
            return when {
                reply.code == 456 -> TranslationFailure.Quota
                reply.code == 400 && "not supported" in message -> TranslationFailure.Unsupported
                else -> failureForStatus(reply.code)
            }
        }
    }
}
