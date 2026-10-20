// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local.llamacpp

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.llm.local.ChatTemplateSource
import me.kafuuneko.rpclient.libs.llm.local.LocalChatMessage
import me.kafuuneko.rpclient.libs.llm.local.LocalChatTemplateException
import me.kafuuneko.rpclient.libs.llm.local.LocalComputeBackend
import me.kafuuneko.rpclient.libs.llm.local.LocalEngineCapabilities
import me.kafuuneko.rpclient.libs.llm.local.LocalEngineKind
import me.kafuuneko.rpclient.libs.llm.local.LocalEngineState
import me.kafuuneko.rpclient.libs.llm.local.LocalEngineUnavailableException
import me.kafuuneko.rpclient.libs.llm.local.LocalFinishReason
import me.kafuuneko.rpclient.libs.llm.local.LocalGenerationEvent
import me.kafuuneko.rpclient.libs.llm.local.LocalGenerationException
import me.kafuuneko.rpclient.libs.llm.local.LocalGpuStatus
import me.kafuuneko.rpclient.libs.llm.local.LocalInferenceEngine
import me.kafuuneko.rpclient.libs.llm.local.LocalInferencePrefs
import me.kafuuneko.rpclient.libs.llm.local.LocalLoadConfig
import me.kafuuneko.rpclient.libs.llm.local.LocalModelDescriptor
import me.kafuuneko.rpclient.libs.llm.local.LocalModelLoadException
import me.kafuuneko.rpclient.libs.llm.local.LocalPromptTooLongException
import me.kafuuneko.rpclient.libs.llm.local.LocalSamplingParams
import me.kafuuneko.rpclient.libs.llm.local.PromptSegment
import me.kafuuneko.rpclient.libs.llm.local.RenderedPrompt
import me.kafuuneko.rpclient.libs.llm.local.StopSequenceFilter
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * [LocalInferenceEngine] backed by llama.cpp, built from source (see `app/src/main/cpp`).
 *
 * - All native calls run on one dedicated thread; llama.cpp contexts are not thread-safe.
 * - Chat templates are the Jinja templates bundled in the GGUF, rendered natively; models without
 *   one fall back to ChatML.
 * - Cancelling [generate]'s collector sets the native abort flag, which stops decoding at the next
 *   token or prompt batch.
 * - The KV cache keeps the previous prompt, so a follow-up message only evaluates the new tokens.
 * - A GGUF's chat template is untrusted code: it renders on its own thread, with a size limit and a
 *   time limit.
 * - The GPU driver loads only after the user turned the GPU on. [mGpuMarker] exists while it
 *   loads, so a driver that kills the process is found at the next start and the GPU turned off.
 */
class LlamaCppEngine(
    private val mNativeLibDir: String,
    private val mKernelCacheDir: String,
    private val mGpuMarker: File
) : LocalInferenceEngine {
    private val mDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, THREAD_NAME).apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val mState = MutableStateFlow<LocalEngineState>(LocalEngineState.Idle)
    private var mInitialized = false

    // Known after the GPU backend was loaded; null before
    @Volatile
    private var mGpuDevices: List<String>? = null

    // Whether loading the GPU backend was attempted in this process; engine thread only
    private var mGpuLoadTried = false

    // Written only on the engine thread, under mHandleLock; other threads abort through it while
    // holding the lock, so the session cannot be freed under them
    @Volatile
    private var mHandle = 0L
    private val mHandleLock = Any()
    private var mModelId: String? = null
    private var mInfo: ModelInfo? = null

    // Templates that ran past the time limit; their thread cannot be stopped, so they are not run again
    private val mStuckTemplates = mutableSetOf<String>()

    override val kind: LocalEngineKind = LocalEngineKind.LlamaCpp

    override val state: StateFlow<LocalEngineState> = mState.asStateFlow()

    override fun capabilities(): LocalEngineCapabilities = LocalEngineCapabilities(
        backends = if (mGpuDevices.isNullOrEmpty()) {
            setOf(LocalComputeBackend.Cpu)
        } else {
            setOf(LocalComputeBackend.Cpu, LocalComputeBackend.Gpu)
        },
        hasExactTokenizer = true,
        usesModelChatTemplate = true
    )

    override suspend fun load(model: LocalModelDescriptor, config: LocalLoadConfig) = withContext(mDispatcher) {
        ensureInitialized()
        if (config.backend == LocalComputeBackend.Gpu) {
            checkGpuCrash()
            if (!LocalInferencePrefs.gpuDriverFailed) ensureGpuLoaded()
        }
        releaseCurrent()
        val path = model.filePath ?: throw LocalModelLoadException()
        mState.value = LocalEngineState.Loading(model.id)
        // "All layers": llama.cpp caps the count at the model's real layer count
        val gpuLayers = if (config.backend == LocalComputeBackend.Gpu && !mGpuDevices.isNullOrEmpty()) ALL_LAYERS else 0
        val (threads, batchThreads) = threadsFor(config)
        val handle = LlamaCppNative.nativeLoad(
            path, config.contextTokens, threads, batchThreads, false, gpuLayers, config.quantizedKvCache
        )
        if (handle == 0L) {
            mState.value = LocalEngineState.Idle
            throw LocalModelLoadException()
        }
        synchronized(mHandleLock) { mHandle = handle }
        mModelId = model.id
        mInfo = readInfo(handle)
        mState.value = LocalEngineState.Ready(model.id)
    }

    override suspend fun unload() = withContext(mDispatcher) {
        releaseCurrent()
        mState.value = LocalEngineState.Idle
    }

    override suspend fun renderChat(messages: List<LocalChatMessage>): RenderedPrompt = withContext(mDispatcher) {
        val info = mInfo ?: throw LocalModelLoadException()
        val template = info.chatTemplate?.takeIf { it.isNotBlank() }
        val result = renderLimited(template ?: CHATML_TEMPLATE, messages, info)
        @Suppress("UNCHECKED_CAST")
        val texts = result[0] as Array<ByteArray>
        val flags = result[1] as BooleanArray
        val segments = texts.mapIndexed { index, bytes -> PromptSegment(String(bytes), flags[index]) }
        RenderedPrompt(
            text = segments.joinToString("") { it.text },
            templateName = if (template == null) FALLBACK_TEMPLATE_NAME else null,
            templateSource = if (template == null) ChatTemplateSource.Fallback else ChatTemplateSource.Model,
            segments = segments
        )
    }

    override suspend fun countTokens(text: String): Int = withContext(mDispatcher) {
        val handle = mHandle.takeIf { it != 0L } ?: throw LocalModelLoadException()
        LlamaCppNative.nativeCountTokens(handle, arrayOf(text.toByteArray()), booleanArrayOf(true))
            .takeIf { it >= 0 } ?: throw LocalGenerationException()
    }

    override suspend fun countPromptTokens(prompt: RenderedPrompt): Int = withContext(mDispatcher) {
        val handle = mHandle.takeIf { it != 0L } ?: throw LocalModelLoadException()
        LlamaCppNative.nativeCountTokens(
            handle,
            prompt.segments.map { it.text.toByteArray() }.toTypedArray(),
            prompt.segments.map { it.isMessageContent }.toBooleanArray()
        ).takeIf { it >= 0 } ?: throw LocalGenerationException()
    }

    override fun generate(prompt: RenderedPrompt, sampling: LocalSamplingParams): Flow<LocalGenerationEvent> = callbackFlow {
        val running = AtomicBoolean(true)
        val activeHandle = AtomicLong(0L)
        val worker = launch(mDispatcher) {
            // Read on the engine thread, so a queued unload or load cannot free it first
            val handle = mHandle
            val modelId = mModelId
            if (handle == 0L || modelId == null) throw LocalModelLoadException()
            activeHandle.set(handle)
            mState.value = LocalEngineState.Generating(modelId)
            try {
                val filter = StopSequenceFilter(sampling.stop)
                // The sink runs on the engine thread inside the native decode loop. Checking isActive
                // also stops a generation whose abort arrived before the native call reset the flag.
                val sink = LlamaTokenSink { bytes ->
                    val text = filter.accept(String(bytes))
                    if (text.isNotEmpty()) trySendBlocking(LocalGenerationEvent.Token(text))
                    !filter.stopped && isActive
                }
                val result = LlamaCppNative.nativeGenerate(
                    handle = handle,
                    parts = prompt.segments.map { it.text.toByteArray() }.toTypedArray(),
                    isInput = prompt.segments.map { it.isMessageContent }.toBooleanArray(),
                    maxTokens = sampling.maxTokens,
                    temperature = sampling.temperature ?: DEFAULT_TEMPERATURE,
                    topP = sampling.topP ?: 1f,
                    topK = sampling.topK ?: 0,
                    minP = sampling.minP ?: 0f,
                    repeatPenalty = sampling.repeatPenalty ?: 1f,
                    seed = sampling.seed ?: RANDOM_SEED,
                    sink = sink
                )
                val (code, promptTokens, completionTokens) = result.toList()
                // Map native finish codes; an abort means the collector is already gone
                val reason = when (code) {
                    LlamaCppNative.FINISH_ABORTED -> return@launch
                    LlamaCppNative.FINISH_PROMPT_TOO_LONG -> throw LocalPromptTooLongException(promptTokens)
                    LlamaCppNative.FINISH_DECODE_ERROR -> throw LocalGenerationException()
                    LlamaCppNative.FINISH_MAX_TOKENS,
                    LlamaCppNative.FINISH_CONTEXT_FULL -> LocalFinishReason.Length
                    else -> LocalFinishReason.Stop
                }
                filter.flush().takeIf { it.isNotEmpty() }?.let { send(LocalGenerationEvent.Token(it)) }
                send(LocalGenerationEvent.Finished(reason, promptTokens, completionTokens))
                channel.close()
            } finally {
                running.set(false)
                mState.value = LocalEngineState.Ready(modelId)
            }
        }
        // The native call ignores coroutine cancellation, so stop it explicitly
        awaitClose { if (running.get()) abortIfCurrent(activeHandle.get()) }
        worker.join()
    }.buffer(Channel.UNLIMITED)

    override suspend fun gpuStatus(): LocalGpuStatus = withContext(mDispatcher) {
        checkGpuCrash()
        when {
            LocalInferencePrefs.gpuDriverFailed -> LocalGpuStatus.DriverFailed
            !hasOpenClDriver() -> {
                // Nothing to offer; a retry from the warning must not leave the setting on
                LocalInferencePrefs.useGpu = false
                LocalGpuStatus.None
            }
            !mGpuLoadTried && !LocalInferencePrefs.useGpu -> LocalGpuStatus.NotLoaded
            else -> {
                runCatching {
                    ensureInitialized()
                    ensureGpuLoaded()
                }
                val name = mGpuDevices?.firstOrNull()
                if (name != null) {
                    LocalGpuStatus.Ready(name)
                } else {
                    // A driver without a usable device: nothing to offer, and loads stay on the CPU
                    LocalInferencePrefs.useGpu = false
                    LocalGpuStatus.None
                }
            }
        }
    }

    override fun abortGeneration() {
        synchronized(mHandleLock) {
            if (mHandle != 0L) LlamaCppNative.nativeAbort(mHandle)
        }
    }

    private fun abortIfCurrent(handle: Long) {
        synchronized(mHandleLock) {
            if (handle != 0L && handle == mHandle) LlamaCppNative.nativeAbort(handle)
        }
    }

    /**
     * Renders [template] on a fresh thread with a large stack, so deeply nested templates do not
     * overflow the engine thread. The native renderer cannot be interrupted: past [RENDER_TIMEOUT_SECONDS]
     * the thread is abandoned and the template is refused from then on.
     */
    private fun renderLimited(template: String, messages: List<LocalChatMessage>, info: ModelInfo): Array<Any> {
        val templateBytes = template.toByteArray()
        if (templateBytes.size > MAX_TEMPLATE_BYTES || template in mStuckTemplates) throw LocalChatTemplateException()
        val task = FutureTask {
            LlamaCppNative.nativeRender(
                template = templateBytes,
                roles = messages.map { it.role.name.lowercase().toByteArray() }.toTypedArray(),
                contents = messages.map { it.content.toByteArray() }.toTypedArray(),
                addGenerationPrompt = true,
                enableThinking = false,
                bos = info.bos.toByteArray(),
                eos = info.eos.toByteArray()
            )
        }
        Thread(null, task, RENDER_THREAD_NAME, RENDER_STACK_BYTES).apply { isDaemon = true }.start()
        return try {
            task.get(RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            mStuckTemplates += template
            throw LocalChatTemplateException()
        } catch (_: ExecutionException) {
            throw LocalChatTemplateException()
        }
    }

    private fun ensureInitialized() {
        if (mInitialized) return
        if (!LlamaCppNative.loadLibrary()) throw LocalEngineUnavailableException()
        LlamaCppNative.nativeInit(mNativeLibDir, mKernelCacheDir)
        mInitialized = true
    }

    /**
     * Loads the GPU backend once per process. The marker file brackets the native call: only a
     * process killed inside it leaves the file behind.
     */
    private fun ensureGpuLoaded() {
        if (mGpuLoadTried) return
        mGpuLoadTried = true
        if (!hasOpenClDriver()) {
            mGpuDevices = emptyList()
            return
        }
        runCatching { mGpuMarker.createNewFile() }
        val loaded = LlamaCppNative.nativeLoadGpu(mNativeLibDir)
        mGpuDevices = if (loaded) LlamaCppNative.nativeGpuDevices().map { String(it) } else emptyList()
        mGpuMarker.delete()
    }

    /** Turns the GPU off when an earlier process died while loading its driver. */
    private fun checkGpuCrash() {
        if (mGpuLoadTried || !mGpuMarker.exists()) return
        mGpuMarker.delete()
        LocalInferencePrefs.useGpu = false
        LocalInferencePrefs.gpuDriverFailed = true
    }

    /** Whether the vendor OpenCL driver exists, checked without loading it. */
    private fun hasOpenClDriver(): Boolean = OPENCL_DRIVER_PATHS.any { File(it).exists() }

    private fun releaseCurrent() {
        val handle = synchronized(mHandleLock) { mHandle.also { mHandle = 0L } }
        if (handle != 0L) LlamaCppNative.nativeFree(handle)
        mModelId = null
        mInfo = null
    }

    private fun readInfo(handle: Long): ModelInfo {
        val values = LlamaCppNative.nativeInfo(handle)
        return ModelInfo(
            chatTemplate = values[0]?.let { String(it) },
            bos = values[1]?.let { String(it) }.orEmpty(),
            eos = values[2]?.let { String(it) }.orEmpty()
        )
    }

    /**
     * Threads for decoding and for prompt evaluation.
     *
     * Measured on a Galaxy S26 Ultra: decoding is bound by memory bandwidth, so 4 threads were as fast
     * as 6 and 8 were 40 % slower; prompt evaluation kept gaining up to 6. Automatic mode uses fast
     * cores only and leaves two cores for the UI.
     */
    private fun threadsFor(config: LocalLoadConfig): Pair<Int, Int> {
        if (config.threads > 0) return config.threads to config.threads
        val cores = Runtime.getRuntime().availableProcessors()
        val batch = minOf(fastCoreCount(cores), cores - 2).coerceIn(MIN_THREADS, MAX_DEFAULT_THREADS)
        return batch.coerceAtMost(MAX_DECODE_THREADS) to batch
    }

    /** Cores whose top frequency is at least 70 % of the fastest one; efficiency cores slow llama.cpp down. */
    private fun fastCoreCount(cores: Int): Int {
        val frequencies = (0 until cores).mapNotNull { core ->
            runCatching { File("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }
                .getOrNull()
        }
        val fastest = frequencies.maxOrNull() ?: return cores
        return frequencies.count { it * FAST_CORE_DIVISOR >= fastest * FAST_CORE_PERCENT }
    }

    private data class ModelInfo(
        val chatTemplate: String?,
        val bos: String,
        val eos: String
    )

    private companion object {
        const val THREAD_NAME = "himitsu-llama"

        // Where vendors install the OpenCL driver; the app may load it through <uses-native-library>
        val OPENCL_DRIVER_PATHS = listOf(
            "/vendor/lib64/libOpenCL.so",
            "/system/vendor/lib64/libOpenCL.so",
            "/system/lib64/libOpenCL.so"
        )
        const val DEFAULT_TEMPERATURE = 0.8f
        const val RANDOM_SEED = -1L
        const val MIN_THREADS = 2
        const val MAX_DEFAULT_THREADS = 6
        const val MAX_DECODE_THREADS = 4
        const val FAST_CORE_PERCENT = 70
        const val FAST_CORE_DIVISOR = 100
        const val ALL_LAYERS = 999
        const val FALLBACK_TEMPLATE_NAME = "chatml"
        const val RENDER_THREAD_NAME = "himitsu-template"
        const val RENDER_STACK_BYTES = 16L * 1024 * 1024
        const val RENDER_TIMEOUT_SECONDS = 15L

        // Real chat templates are a few KB; the largest known ones stay well under 64 KB
        const val MAX_TEMPLATE_BYTES = 128 * 1024

        // Used when a GGUF has no chat template; ChatML is the most widely trained format
        const val CHATML_TEMPLATE =
            "{% for message in messages %}{{ '<|im_start|>' + message['role'] + '\\n' + message['content'] + '<|im_end|>\\n' }}{% endfor %}" +
                "{% if add_generation_prompt %}{{ '<|im_start|>assistant\\n' }}{% endif %}"
    }
}
