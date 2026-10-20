// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/**
 * Removes credentials from debug request logs before they are stored (P-07).
 *
 * Two independent passes, so one missing case does not leak a key:
 * - every exact occurrence of a known secret (the provider's API key and custom header values)
 *   is replaced, wherever it appears, even in non-JSON text such as streamed chunks
 * - in JSON, the value of any field whose name looks like a credential is replaced
 */
object RequestLogRedactor {
    const val REDACTED = "[REDACTED]"

    /** Secrets shorter than this are not replaced verbatim; they would match ordinary text. */
    private const val MIN_SECRET_LENGTH = 8

    private val SENSITIVE_NAMES = setOf(
        "authorization", "proxy-authorization", "api_key", "apikey", "api-key", "x-api-key",
        "x-goog-api-key", "key", "token", "access_token", "refresh_token", "id_token",
        "secret", "client_secret", "password", "cookie", "set-cookie"
    )

    private val GSON = GsonBuilder().disableHtmlEscaping().serializeNulls().create()

    /**
     * Returns [text] with every secret removed.
     *
     * @param secrets values known to be secret, such as the API key; blank and very short
     *   values are ignored
     */
    fun redact(text: String, secrets: Collection<String>): String {
        if (text.isEmpty()) return text
        // Replace known secret values anywhere, longest first so overlapping values stay hidden
        var result = text
        secrets.map(String::trim)
            .filter { it.length >= MIN_SECRET_LENGTH }
            .distinct()
            .sortedByDescending(String::length)
            .forEach { secret -> result = result.replace(secret, REDACTED) }
        // Then hide credential-looking fields when the text is JSON
        val json = runCatching { JsonParser.parseString(result) }.getOrNull() ?: return result
        if (!json.isJsonObject && !json.isJsonArray) return result
        return GSON.toJson(redactFields(json))
    }

    /**
     * Collects the secret values of a provider: its API key and every custom header value.
     */
    fun providerSecrets(apiKey: String, customHeadersJson: String): List<String> {
        val headerValues = runCatching {
            JsonParser.parseString(customHeadersJson).asJsonObject.entrySet()
                .mapNotNull { (_, value) -> value.takeIf { it.isJsonPrimitive }?.asString }
        }.getOrDefault(emptyList())
        return listOf(apiKey) + headerValues
    }

    private fun redactFields(element: JsonElement): JsonElement = when {
        element.isJsonObject -> JsonObject().also { copy ->
            element.asJsonObject.entrySet().forEach { (name, value) ->
                val hidden = name.lowercase() in SENSITIVE_NAMES && !value.isJsonNull
                copy.add(name, if (hidden) JsonPrimitive(REDACTED) else redactFields(value))
            }
        }
        element.isJsonArray -> JsonArray().also { copy ->
            element.asJsonArray.forEach { copy.add(redactFields(it)) }
        }
        else -> element
    }
}
