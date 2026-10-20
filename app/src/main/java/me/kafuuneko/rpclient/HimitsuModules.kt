// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient

import me.kafuuneko.rpclient.libs.llm.local.localInferenceModule
import org.koin.core.module.Module

/**
 * Koin modules contributed by Himitsu work tracks, loaded after the upstream [appModules].
 *
 * Each track declares its module in its own file and appends one entry here, instead of
 * editing [appModules]. This keeps parallel tracks and upstream merges free of conflicts.
 */
internal val himitsuModules: List<Module> = listOf(
    localInferenceModule, // feat/local-inference
)
