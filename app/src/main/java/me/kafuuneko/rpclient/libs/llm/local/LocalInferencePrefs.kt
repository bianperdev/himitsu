// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

import com.chibatching.kotpref.KotprefModel

/**
 * Device-level settings of on-device inference.
 *
 * They describe this phone, not a provider, so they live here rather than in a provider row. None
 * of them is sensitive.
 */
object LocalInferencePrefs : KotprefModel() {
    override val kotprefName: String = "local_inference"

    /** CPU threads; [AUTO_THREADS] lets the engine pick from the number of cores. */
    var threads by intPref(default = AUTO_THREADS)

    /** Offload layers to the GPU when the device offers a supported one. */
    var useGpu by booleanPref(default = false)

    /** Loading the GPU driver closed the app; set until the user turns the GPU on again. */
    var gpuDriverFailed by booleanPref(default = false)

    /** A model already generated a reply on the GPU, so its driver is no longer watched. */
    var gpuVerified by booleanPref(default = false)

    /** Store the KV cache in 8-bit instead of 16-bit: half the memory, slightly lower quality. */
    var quantizedKvCache by booleanPref(default = false)

    /** Seconds in the background before the model is released; [KEEP_LOADED] never releases it. */
    var unloadDelaySeconds by intPref(default = DEFAULT_UNLOAD_DELAY_SECONDS)

    /** Load settings for a provider with [contextTokens], taken from these preferences. */
    fun loadConfig(contextTokens: Int): LocalLoadConfig = LocalLoadConfig(
        contextTokens = contextTokens,
        threads = threads,
        backend = if (useGpu) LocalComputeBackend.Gpu else LocalComputeBackend.Cpu,
        quantizedKvCache = quantizedKvCache
    )

    const val AUTO_THREADS = 0
    const val KEEP_LOADED = -1
    const val DEFAULT_UNLOAD_DELAY_SECONDS = 120
}
