// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogClient
import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol

/**
 * Model catalog of a local provider: the models stored on this device, with no network call.
 *
 * It plugs into the provider editor's existing "query models" action.
 */
class LocalModelCatalogClient(private val mModels: LocalModelRepository) : LLMModelCatalogClient {
    override suspend fun listModels(): List<LLMAvailableModel> = mModels.listModels().map { model ->
        LLMAvailableModel(
            id = model.id,
            displayName = model.displayName,
            contextTokens = model.trainedContextTokens.takeIf { it > 0 },
            inputModalities = setOf(TEXT_MODALITY)
        )
    }

    private companion object {
        const val TEXT_MODALITY = "text"
    }
}

/** True when this protocol talks to a configured network endpoint, so it needs a base URL. */
val LLMProviderProtocol.usesNetworkEndpoint: Boolean
    get() = this != LLMProviderProtocol.Local
