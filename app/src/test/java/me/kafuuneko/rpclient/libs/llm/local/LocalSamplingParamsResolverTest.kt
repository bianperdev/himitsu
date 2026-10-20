// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalSamplingParamsResolverTest {
    @Test
    fun readsLlamaServerSamplersFromThePatch() {
        val params = LocalSamplingParamsResolver.resolve(
            provider("""{"top_k":40,"min_p":0.05,"repeat_penalty":1.1,"seed":42}"""),
            LLMGenerationOptions(stop = listOf("\nUser:"))
        )

        assertEquals(40, params.topK)
        assertEquals(0.05f, params.minP!!, 1e-6f)
        assertEquals(1.1f, params.repeatPenalty!!, 1e-6f)
        assertEquals(42L, params.seed)
        assertEquals(listOf("\nUser:"), params.stop)
    }

    @Test
    fun takesTemperatureTopPAndMaxTokensFromTheProviderUnlessTheRequestOverridesThem() {
        val params = LocalSamplingParamsResolver.resolve(
            provider("{}"),
            LLMGenerationOptions(temperature = 1.2f)
        )

        assertEquals(1.2f, params.temperature!!, 1e-6f)
        assertEquals(0.9f, params.topP!!, 1e-6f)
        assertEquals(512, params.maxTokens)
    }

    @Test
    fun omitsTemperatureAndTopPWhenTheProviderDoesNotSendThem() {
        val params = LocalSamplingParamsResolver.resolve(
            provider("{}").copy(sendTemperature = false, sendTopP = false),
            LLMGenerationOptions()
        )

        assertNull(params.temperature)
        assertNull(params.topP)
    }

    @Test
    fun ignoresWrongTypesAndOutOfRangeValues() {
        val params = LocalSamplingParamsResolver.resolve(
            provider("""{"top_k":"40","min_p":3,"repeat_penalty":-1,"seed":1.5}"""),
            LLMGenerationOptions()
        )

        assertNull(params.topK)
        assertNull(params.minP)
        assertNull(params.repeatPenalty)
        assertNull(params.seed)
    }

    @Test
    fun survivesAPatchThatIsNotAJsonObject() {
        val params = LocalSamplingParamsResolver.resolve(provider("[1, 2"), LLMGenerationOptions())

        assertNull(params.topK)
        assertEquals(512, params.maxTokens)
    }

    private fun provider(patch: String) = LLMProviderConfig(
        name = "Local",
        providerType = LLMProviderType.Local,
        protocol = LLMProviderProtocol.Local,
        baseUrl = "",
        model = FakeLocalInferenceEngine.MODEL_ID,
        requestBodyPatchJson = patch,
        temperature = 0.7f,
        topP = 0.9f,
        maxTokens = 512
    )
}
