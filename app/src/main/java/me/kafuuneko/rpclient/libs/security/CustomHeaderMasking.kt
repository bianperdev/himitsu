// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Hides the values of custom HTTP headers in the provider editor (P-04).
 *
 * Header names stay visible so the user knows what is configured; values such as
 * `Authorization` are replaced by [HIDDEN_VALUE]. When the user edits the masked JSON, every
 * value still equal to [HIDDEN_VALUE] keeps its original, so secrets never need to be shown or
 * retyped to change another header.
 */
object CustomHeaderMasking {
    const val HIDDEN_VALUE = "••••••••"

    /**
     * Returns [json] with every header value hidden.
     *
     * @return the masked JSON, or an empty string when [json] is blank or not a JSON object, so
     *   a malformed value is never shown either
     */
    fun mask(json: String): String {
        val headers = parseObject(json) ?: return ""
        val masked = JsonObject()
        headers.keySet().forEach { name -> masked.addProperty(name, HIDDEN_VALUE) }
        return GSON.toJson(masked)
    }

    /**
     * Merges an edited masked JSON with the real headers.
     *
     * - A value still equal to [HIDDEN_VALUE] takes the real value of the same header name.
     * - A hidden value whose header has no real value (a renamed header) is dropped instead of
     *   sending the mask characters to the server.
     * - Any other value is a new value typed by the user and is kept.
     *
     * @return the real JSON to store, or [edited] unchanged when it is not a JSON object, so the
     *   caller's validation reports the error
     */
    fun unmask(edited: String, plain: String): String {
        val editedHeaders = parseObject(edited) ?: return edited
        val plainHeaders = parseObject(plain) ?: JsonObject()
        val merged = JsonObject()
        editedHeaders.entrySet().forEach { (name, value) ->
            val isHidden = value.isJsonPrimitive && value.asString == HIDDEN_VALUE
            when {
                !isHidden -> merged.add(name, value)
                plainHeaders.has(name) -> merged.add(name, plainHeaders.get(name))
            }
        }
        return GSON.toJson(merged)
    }

    private fun parseObject(json: String): JsonObject? {
        if (json.isBlank()) return null
        return runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
    }

    private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
}
