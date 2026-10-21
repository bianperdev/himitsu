// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.provider

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import me.kafuuneko.rpclient.libs.translation.TranslationException
import me.kafuuneko.rpclient.libs.translation.TranslationFailure
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

// HTTP helpers shared by the translation clients. Nothing here logs: request bodies hold chat
// text and headers hold keys, so failures carry only a TranslationFailure.

private val JsonMediaType = "application/json; charset=utf-8".toMediaType()

/** Largest response body read; a translation reply is far smaller, anything bigger is refused. */
private const val MAX_RESPONSE_BYTES = 4L * 1024 * 1024

/** Status and body of a finished HTTP exchange. [toString] never prints the body. */
internal class TranslationHttpReply(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299

    override fun toString(): String = "TranslationHttpReply(code=$code, [REDACTED])"
}

/**
 * Sends [request] and reads the reply, mapping transport errors to [TranslationFailure.Network].
 * Cancelling the coroutine cancels the call. The shared client does not follow redirects, so a
 * 3xx comes back as a reply.
 */
internal suspend fun OkHttpClient.sendTranslationRequest(request: Request): TranslationHttpReply {
    val call = newCall(request)
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resumeWithException(TranslationException(TranslationFailure.Network, e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                // A broken stream while reading must still resume the coroutine, never escape the callback
                val result = runCatching { response.use { TranslationHttpReply(it.code, it.peekBody(MAX_RESPONSE_BYTES).string()) } }
                if (continuation.isCancelled) return
                result.fold(
                    onSuccess = { continuation.resume(it) },
                    onFailure = { continuation.resumeWithException(TranslationException(TranslationFailure.Network, it)) }
                )
            }
        })
    }
}

/** Parses [baseUrl] and appends [path] segments; a malformed URL is the user's configuration. */
internal fun translationEndpoint(baseUrl: String, vararg path: String): HttpUrl {
    val base = baseUrl.trim().trimEnd('/').toHttpUrlOrNull()
        ?: throw TranslationException(TranslationFailure.Invalid)
    return base.newBuilder().apply { path.forEach { addPathSegments(it) } }.build()
}

/** JSON request body; Gson keeps the clients testable on the JVM. */
internal fun JsonElement.toJsonRequestBody(): RequestBody = toString().toRequestBody(JsonMediaType)

/** Adds the user's extra headers from [customHeadersJson]; a malformed value adds none. */
internal fun Request.Builder.applyCustomHeaders(customHeadersJson: String): Request.Builder {
    if (customHeadersJson.isBlank()) return this
    val json = runCatching { JsonParser.parseString(customHeadersJson).asJsonObject }.getOrNull() ?: return this
    json.entrySet().forEach { (name, value) ->
        if (value.isJsonPrimitive) runCatching { header(name, value.asString) }
    }
    return this
}

/** Parses a successful reply as a JSON value, or fails as [TranslationFailure.Invalid]. */
internal fun TranslationHttpReply.json(): JsonElement =
    runCatching { JsonParser.parseString(body) }.getOrNull()?.takeUnless { it.isJsonNull }
        ?: throw TranslationException(TranslationFailure.Invalid)

/** The reply as a JSON object, or null when it is not one. */
internal fun TranslationHttpReply.jsonObjectOrNull(): JsonObject? =
    runCatching { JsonParser.parseString(body) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject

/** String value of [name], or null when missing or not a string. */
internal fun JsonObject.stringOrNull(name: String): String? {
    val element = get(name) ?: return null
    if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return null
    return element.asString
}

/**
 * Failure for an HTTP status that every service treats alike; services refine it from the error
 * body before falling back here.
 */
internal fun failureForStatus(code: Int): TranslationFailure = when (code) {
    401, 403 -> TranslationFailure.Auth
    429 -> TranslationFailure.Quota
    in 300..399, 400, 404, 405, 413, 414, 415, 422 -> TranslationFailure.Invalid
    else -> TranslationFailure.Network
}

/** Primary language subtag in lower case ("pt-BR" and "PT" become "pt"), or null when empty. */
internal fun primaryLanguageTag(tag: String?): String? =
    tag?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase()?.takeIf { it.isNotEmpty() }

/** Throws [TranslationFailure.Invalid] when a reply does not have one translation per text. */
internal fun List<String>.requireSameSizeAs(texts: List<String>): List<String> {
    if (size != texts.size) throw TranslationException(TranslationFailure.Invalid)
    return this
}

private val HTML_ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|quot|amp|lt|gt|apos|nbsp);")

/**
 * Decodes the HTML entities a service writes in `format=html` replies (`&#39;`, `&quot;`,
 * `&amp;`, `&lt;`, `&gt;` and numeric ones) in a single pass, so "&amp;lt;" becomes "&lt;" and
 * not "<". Placeholder tags such as `<x id="1"/>` are plain markup and stay as they are.
 */
internal fun decodeHtmlEntities(text: String): String {
    if ('&' !in text) return text
    return HTML_ENTITY.replace(text) { match ->
        val entity = match.groupValues[1]
        when {
            entity.startsWith("#x") || entity.startsWith("#X") -> entity.substring(2).toIntOrNull(16)?.toCodePointString()
            entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.toCodePointString()
            else -> when (entity) {
                "quot" -> "\""
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "apos" -> "'"
                else -> "\u00A0"
            }
        } ?: match.value
    }
}

private fun Int.toCodePointString(): String? =
    takeIf { Character.isValidCodePoint(it) && it != 0 }?.let { String(Character.toChars(it)) }
