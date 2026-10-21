// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient

import me.kafuuneko.rpclient.libs.disguise.disguiseModule
import me.kafuuneko.rpclient.libs.llm.local.localInferenceModule
import me.kafuuneko.rpclient.libs.security.securityModule
import me.kafuuneko.rpclient.libs.translation.core.translationCoreModule
import me.kafuuneko.rpclient.libs.translation.provider.translationProvidersModule
import me.kafuuneko.rpclient.libs.translation.translationModule
import org.koin.core.module.Module

/**
 * Koin modules contributed by Himitsu work tracks, loaded after the upstream [appModules].
 *
 * Each track declares its module in its own file and appends one entry here, instead of
 * editing [appModules]. This keeps parallel tracks and upstream merges free of conflicts.
 */
internal val himitsuModules: List<Module> = listOf(
    securityModule, // feat/security
    localInferenceModule, // feat/local-inference
    disguiseModule, // feat/disguise: after securityModule, it rebinds UnlockEntry
    translationModule, // feat/translation-*
    translationProvidersModule, // feat/translation-providers: after translationModule, it rebinds TranslationClientFactory
    translationCoreModule, // feat/translation-core: after translationModule, it rebinds its stand-ins
)
