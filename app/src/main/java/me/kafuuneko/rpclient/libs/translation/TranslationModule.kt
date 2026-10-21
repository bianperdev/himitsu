// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation

import me.kafuuneko.rpclient.libs.room.AppDatabase
import org.koin.dsl.module

/**
 * Koin module of the translation feature, appended to `himitsuModules`. The groundwork binds
 * stand-ins; `feat/translation-providers` replaces [TranslationClientFactory] and
 * `feat/translation-core` replaces [LanguageDetector], [TextProtector] and [ChatTranslator].
 */
val translationModule = module {
    single { get<AppDatabase>().getTranslationProviderDao() }
    single { get<AppDatabase>().getChatMessageTranslationDao() }
    single { get<AppDatabase>().getGroupChatMessageTranslationDao() }
    single { get<AppDatabase>().getChatTranslationSettingDao() }
    single { get<AppDatabase>().getGroupChatTranslationSettingDao() }
    single<TranslationClientFactory> { FakeTranslationClientFactory() }
    single<LanguageDetector> { NoLanguageDetector() }
    single<ChatTranslator> { NoopChatTranslator() }
}
