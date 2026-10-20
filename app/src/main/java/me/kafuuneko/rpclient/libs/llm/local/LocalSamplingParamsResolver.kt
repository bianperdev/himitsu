// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.resolveFor

/**
 * Builds [LocalSamplingParams] for a local provider.
 *
 * - Temperature, top-p, max tokens and stop sequences come from the provider and the request,
 *   with the same rules as remote providers.
 * - Samplers that remote providers do not have are read from the provider's request body patch,
 *   using llama-server parameter names (`top_k`, `min_p`, `repeat_penalty`, `seed`), so users
 *   coming from SillyTavern recognize them and no database column is needed.
 * - A value with the wrong type or out of range is ignored instead of failing the generation.
 */
object LocalSamplingParamsResolver {
    /** Resolves the sampling settings for one request. */
    fun resolve(provider: LLMProviderConfig, options: LLMGenerationOptions): LocalSamplingParams {
        val resolved = options.resolveFor(provider)
        val patch = parsePatch(provider.requestBodyPatchJson)
        return LocalSamplingParams(
            maxTokens = resolved.maxTokens,
            temperature = resolved.temperature,
            topP = resolved.topP,
            topK = patch.intOrNull(KEY_TOP_K)?.takeIf { it > 0 },
            minP = patch.floatOrNull(KEY_MIN_P)?.takeIf { it in 0f..1f },
            repeatPenalty = patch.floatOrNull(KEY_REPEAT_PENALTY)?.takeIf { it > 0f },
            seed = patch.longOrNull(KEY_SEED)?.takeIf { it >= 0 },
            stop = resolved.stop
        )
    }

    private fun parsePatch(json: String): JsonObject {
        val element = runCatching { JsonParser.parseString(json.ifBlank { "{}" }) }.getOrNull()
        return element?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
    }

    private fun JsonObject.numberOrNull(key: String): JsonElement? {
        val value = get(key) ?: return null
        return value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        val number = numberOrNull(key)?.asDouble ?: return null
        return number.takeIf { it == Math.floor(it) && it in 0.0..Int.MAX_VALUE.toDouble() }?.toInt()
    }

    private fun JsonObject.longOrNull(key: String): Long? {
        val number = numberOrNull(key)?.asDouble ?: return null
        return number.takeIf { it == Math.floor(it) && it in 0.0..MAX_SAFE_SEED }?.toLong()
    }

    private fun JsonObject.floatOrNull(key: String): Float? {
        return numberOrNull(key)?.asDouble?.takeIf { it.isFinite() }?.toFloat()
    }

    private const val KEY_TOP_K = "top_k"
    private const val KEY_MIN_P = "min_p"
    private const val KEY_REPEAT_PENALTY = "repeat_penalty"
    private const val KEY_SEED = "seed"

    /** Largest seed that survives a round trip through a JSON double without losing precision. */
    private const val MAX_SAFE_SEED = 9_007_199_254_740_991.0
}
