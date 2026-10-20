// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

import android.content.Context
import android.content.pm.ApplicationInfo
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Koin module of the feat/local-inference track, appended to `himitsuModules`.
 *
 * The fake engine is registered only in debuggable builds, so release users never see it.
 */
val localInferenceModule = module {
    single { LocalEngineRegistry(availableEngines(androidContext())) }
    singleOf(::LocalModelRepository)
    singleOf(::LocalModelRuntime)
    singleOf(::LocalLLMClientFactory)
    singleOf(::LocalModelCatalogClient)
}

private fun availableEngines(context: Context): List<LocalInferenceEngine> {
    val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    return buildList {
        if (isDebuggable) add(FakeLocalInferenceEngine())
    }
}
