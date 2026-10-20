// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

import me.kafuuneko.rpclient.libs.llm.GenerationFailure
import me.kafuuneko.rpclient.libs.llm.LLMProviderRequestException
import me.kafuuneko.rpclient.libs.llm.LocalGenerationFailure
import me.kafuuneko.rpclient.libs.llm.classifyGenerationFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalGenerationFailureTest {
    @Test
    fun classifiesEveryLocalFailureEvenWhenTheRepositoryWrapsIt() {
        val cases = mapOf(
            LocalModelUnavailableException() to LocalGenerationFailure.ModelMissing,
            LocalModelTooLargeException() to LocalGenerationFailure.TooLarge,
            LocalModelLoadException() to LocalGenerationFailure.LoadFailed,
            LocalChatTemplateException() to LocalGenerationFailure.TemplateFailed,
            LocalEngineUnavailableException() to LocalGenerationFailure.EngineUnavailable,
            LocalGenerationException() to LocalGenerationFailure.GenerationFailed
        )

        cases.forEach { (error, kind) ->
            val wrapped = LLMProviderRequestException(providerName = "On-device model", requestCause = error)
            assertEquals(GenerationFailure.Local(kind), classifyGenerationFailure(wrapped))
        }
    }
}
