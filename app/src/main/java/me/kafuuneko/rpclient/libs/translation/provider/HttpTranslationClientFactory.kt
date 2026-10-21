// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.provider

import me.kafuuneko.rpclient.libs.translation.TranslationClient
import me.kafuuneko.rpclient.libs.translation.TranslationClientFactory
import me.kafuuneko.rpclient.libs.translation.TranslationProviderConfig
import me.kafuuneko.rpclient.libs.translation.model.TranslationProvider
import me.kafuuneko.rpclient.libs.translation.model.TranslationProviderType
import okhttp3.OkHttpClient

/**
 * Builds the client of each provider type.
 *
 * Every HTTP client shares the app's [OkHttpClient], which does not follow redirects, so a key
 * header can never be carried to another host. The AI provider client goes through
 * [LlmTranslationGateway] and so through the AI provider's own network rules.
 */
class HttpTranslationClientFactory(
    private val mOkHttpClient: OkHttpClient,
    private val mLlmGateway: LlmTranslationGateway
) : TranslationClientFactory {
    override fun create(config: TranslationProviderConfig): TranslationClient = when (config.type) {
        TranslationProviderType.LibreTranslate -> LibreTranslateClient(mOkHttpClient, config)
        TranslationProviderType.DeepL -> DeepLClient(mOkHttpClient, config)
        TranslationProviderType.GoogleCloud -> GoogleCloudTranslationClient(mOkHttpClient, config)
        TranslationProviderType.LlmProvider -> LlmTranslationClient(mLlmGateway, config.llmProviderId)
    }
}

/** The runtime configuration of a stored provider. */
fun TranslationProvider.toConfig(): TranslationProviderConfig = TranslationProviderConfig(
    id = id,
    name = name,
    type = type,
    baseUrl = baseUrl,
    apiKey = apiKey,
    llmProviderId = llmProviderId,
    customHeadersJson = customHeadersJson
)
