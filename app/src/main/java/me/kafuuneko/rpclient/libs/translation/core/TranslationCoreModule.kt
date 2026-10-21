// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.core

import me.kafuuneko.rpclient.libs.translation.ChatTranslator
import me.kafuuneko.rpclient.libs.translation.LanguageDetector
import me.kafuuneko.rpclient.libs.translation.TextProtector
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module of the `feat/translation-core` track, appended to `himitsuModules` right after
 * `translationModule`: Koin keeps the last definition, so these replace the groundwork's
 * stand-ins. The DAOs and the [me.kafuuneko.rpclient.libs.translation.TranslationClientFactory]
 * come from the other translation modules.
 */
internal val translationCoreModule = module {
    single<TranslationCoreSettings> { PrefsTranslationCoreSettings(androidContext()) }
    single<LanguageDetector> { AndroidLanguageDetector(androidContext()) }
    single<TextProtector> { RegexTextProtector() }
    single<ChatTranslator> {
        ChatTranslatorImpl(
            providerDao = get(),
            chatMessageDao = get(),
            groupMessageDao = get(),
            chatSettingDao = get(),
            groupSettingDao = get(),
            clientFactory = get(),
            detector = get(),
            protector = get(),
            settings = get()
        )
    }
}
