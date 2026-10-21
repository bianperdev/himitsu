// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.provider

import me.kafuuneko.rpclient.libs.translation.TranslationClientFactory
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Koin module of `feat/translation-providers`, loaded right after `translationModule` so its
 * [TranslationClientFactory] replaces the groundwork's fake one (Koin keeps the last definition).
 */
val translationProvidersModule = module {
    single<LlmTranslationGateway> { LLMRepositoryTranslationGateway(get()) }
    single<TranslationClientFactory> { HttpTranslationClientFactory(get(), get()) }
    singleOf(::TranslationProviderRepository)
}
