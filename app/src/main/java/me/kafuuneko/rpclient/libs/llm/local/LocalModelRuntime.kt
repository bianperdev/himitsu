// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The engines available in this build, keyed by kind. */
class LocalEngineRegistry(engines: List<LocalInferenceEngine>) {
    private val mEngines: Map<LocalEngineKind, LocalInferenceEngine> = engines.associateBy { it.kind }

    /** Returns the engine of [kind], or null when this build or device does not offer it. */
    fun find(kind: LocalEngineKind): LocalInferenceEngine? = mEngines[kind]

    /** Every engine registered in this build. */
    val all: Collection<LocalInferenceEngine>
        get() = mEngines.values
}

/**
 * Lists the models a local provider can use.
 *
 * - The fake engine contributes one built-in model.
 * - Imported GGUF files are added with the llama.cpp engine.
 */
class LocalModelRepository(private val mEngines: LocalEngineRegistry) {
    /** Every model the user can pick, in display order. */
    suspend fun listModels(): List<LocalModelDescriptor> {
        val builtIn = mutableListOf<LocalModelDescriptor>()
        if (mEngines.find(LocalEngineKind.Fake) != null) {
            builtIn += LocalModelDescriptor(
                id = FakeLocalInferenceEngine.MODEL_ID,
                displayName = FAKE_MODEL_DISPLAY_NAME,
                engine = LocalEngineKind.Fake
            )
        }
        return builtIn
    }

    /** Returns the model with [id], or null when it was removed or is not offered in this build. */
    suspend fun findModel(id: String): LocalModelDescriptor? = listModels().firstOrNull { it.id == id }

    private companion object {
        // Developer tool name shown only in debuggable builds, like a model id
        const val FAKE_MODEL_DISPLAY_NAME = "Fake engine (debug)"
    }
}

/** The model a local provider points to is not available any more. */
class LocalModelUnavailableException : IllegalStateException("The selected local model is not available")

/**
 * Owns the model that is loaded in memory.
 *
 * - At most one model is loaded at a time; switching models unloads the previous one first.
 * - Loading and generation are serialized with a mutex, so a summary request waits for a chat
 *   reply instead of running beside it and doubling memory use.
 */
class LocalModelRuntime(
    private val mEngines: LocalEngineRegistry,
    private val mModels: LocalModelRepository
) {
    private val mMutex = Mutex()
    private var mLoaded: LoadedModel? = null

    /**
     * Runs [block] with the engine that has [modelId] loaded, loading it first when needed.
     *
     * @throws LocalModelUnavailableException when the model or its engine is not available.
     */
    suspend fun <T> withModel(
        modelId: String,
        config: LocalLoadConfig,
        block: suspend (engine: LocalInferenceEngine, model: LocalModelDescriptor) -> T
    ): T = mMutex.withLock {
        val model = mModels.findModel(modelId) ?: throw LocalModelUnavailableException()
        val engine = mEngines.find(model.engine) ?: throw LocalModelUnavailableException()
        val loaded = mLoaded
        if (loaded == null || loaded.model != model || loaded.config != config) {
            // Release the previous model before loading, so two never share memory
            loaded?.engine?.unload()
            mLoaded = null
            engine.load(model, config)
            mLoaded = LoadedModel(engine, model, config)
        }
        block(engine, model)
    }

    /** Unloads the current model, waiting for a running generation to finish first. */
    suspend fun release() = mMutex.withLock {
        mLoaded?.engine?.unload()
        mLoaded = null
    }

    private data class LoadedModel(
        val engine: LocalInferenceEngine,
        val model: LocalModelDescriptor,
        val config: LocalLoadConfig
    )
}
