// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local.llamacpp

import androidx.annotation.Keep

/**
 * Receives generated text from native code, one complete UTF-8 piece at a time.
 *
 * Kept from minification because `himitsu_llama_jni.cpp` looks it up by name.
 */
@Keep
fun interface LlamaTokenSink {
    /** Returns false to stop generation, for example when a stop sequence matched. */
    fun onPiece(bytes: ByteArray): Boolean
}

/**
 * JNI entry points of `libhimitsu_llama.so`; see `app/src/main/cpp/himitsu_llama_jni.cpp`.
 *
 * - Text crosses the boundary as UTF-8 byte arrays, because JNI strings use modified UTF-8.
 * - Every call except [nativeAbort] must run on the engine's single thread.
 * - Handles are raw pointers: each one must be freed exactly once with [nativeFree].
 */
@Keep
internal object LlamaCppNative {
    // Finish codes, mirrored from the native side
    const val FINISH_EOG = 0
    const val FINISH_MAX_TOKENS = 1
    const val FINISH_CONTEXT_FULL = 2
    const val FINISH_ABORTED = 3
    const val FINISH_SINK_STOP = 4
    const val FINISH_PROMPT_TOO_LONG = -1
    const val FINISH_DECODE_ERROR = -2

    /** Loads the native library; false when it is missing for this ABI. */
    fun loadLibrary(): Boolean = runCatching { System.loadLibrary("himitsu_llama") }.isSuccess

    /** Silences native logs, loads the best CPU backend from [nativeLibDir] and initializes llama.cpp. */
    @JvmStatic external fun nativeInit(nativeLibDir: String)

    /** Loads a GGUF model; returns 0 on failure. */
    @JvmStatic external fun nativeLoad(path: String, contextTokens: Int, threads: Int, vocabOnly: Boolean): Long

    @JvmStatic external fun nativeFree(handle: Long)

    /** Returns {chat template or null, BOS text, EOS text, trained context length, model name or null}. */
    @JvmStatic external fun nativeInfo(handle: Long): Array<ByteArray?>

    /** Renders a Jinja chat template; returns {Array<ByteArray> parts, BooleanArray isInput}. */
    @JvmStatic external fun nativeRender(
        template: ByteArray,
        roles: Array<ByteArray>,
        contents: Array<ByteArray>,
        addGenerationPrompt: Boolean,
        enableThinking: Boolean,
        bos: ByteArray,
        eos: ByteArray
    ): Array<Any>

    /** Counts the tokens of rendered parts, including BOS; -1 on failure. */
    @JvmStatic external fun nativeCountTokens(handle: Long, parts: Array<ByteArray>, isInput: BooleanArray): Int

    /** Generates a reply; returns {finish code, prompt tokens, completion tokens}. */
    @JvmStatic external fun nativeGenerate(
        handle: Long,
        parts: Array<ByteArray>,
        isInput: BooleanArray,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        minP: Float,
        repeatPenalty: Float,
        seed: Long,
        sink: LlamaTokenSink
    ): IntArray

    /** Asks a running generation or prompt evaluation to stop; safe from any thread. */
    @JvmStatic external fun nativeAbort(handle: Long)
}
