// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.room.Room
import me.kafuuneko.rpclient.libs.llm.local.llamacpp.LlamaCppEngine
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelDatabase
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelDownloadTracker
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelDownloader
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelFiles
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelImporter
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelLibrary
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.io.File

/**
 * Koin module of the feat/local-inference track, appended to `himitsuModules`.
 *
 * The fake engine is registered only in debuggable builds, so release users never see it.
 */
val localInferenceModule = module {
    single { LocalEngineRegistry(availableEngines(androidContext())) }
    single {
        // In noBackupFilesDir, so Android backups never export the model list
        val path = File(androidContext().noBackupFilesDir, LocalModelDatabase.FILE_NAME).path
        Room.databaseBuilder(androidContext(), LocalModelDatabase::class.java, path).build()
    }
    single { get<LocalModelDatabase>().localModelDao() }
    // Partial copies left by a killed process are useless; nothing can be importing yet
    single { LocalModelFiles(androidContext()).also { it.deletePartialFiles() } }
    singleOf(::LocalModelImporter)
    singleOf(::LocalModelDownloader)
    singleOf(::LocalModelDownloadTracker)
    singleOf(::LocalModelLibrary)
    singleOf(::LocalModelRepository)
    singleOf(::LocalModelMemoryGuard)
    singleOf(::LocalModelRuntime)
    // Created with the app, so it sees every screen start and stop
    single(createdAtStart = true) { LocalModelLifecycle(androidApplication(), get()) }
    singleOf(::LocalGenerationKeeper)
    singleOf(::LocalLLMClientFactory)
    singleOf(::LocalModelCatalogClient)
    singleOf(::LocalPromptPreviewer)
}

private fun availableEngines(context: Context): List<LocalInferenceEngine> {
    val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    // llama.cpp is built for these ABIs only, see app/build.gradle.kts
    val hasLlamaCppAbi = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "x86_64" }
    return buildList {
        if (isDebuggable) add(FakeLocalInferenceEngine())
        if (hasLlamaCppAbi) {
            val kernelCache = File(context.cacheDir, "opencl-kernels").apply { mkdirs() }
            val gpuMarker = File(context.noBackupFilesDir, GPU_LOAD_MARKER_FILE)
            add(LlamaCppEngine(context.applicationInfo.nativeLibraryDir, kernelCache.path, gpuMarker))
        }
    }
}

/** Exists only while the GPU driver loads; see [LlamaCppEngine]. Not backed up, like the models. */
private const val GPU_LOAD_MARKER_FILE = "llama_gpu_loading"
