// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.libs.llm.local.model.GgufHeaderReader
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelMemoryEstimator
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelMemoryFit
import java.io.File

/** The model needs more memory than this device has. */
class LocalModelTooLargeException : LocalInferenceException("The local model does not fit in this device's memory")

/**
 * Refuses to load a model that cannot fit in RAM, instead of letting Android kill the app.
 *
 * Tighter fits only warn, on the models screen; this check stops just the impossible case.
 */
class LocalModelMemoryGuard(private val mContext: Context) {
    private val mTotalRamBytes: Long by lazy {
        val info = ActivityManager.MemoryInfo()
        mContext.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        info.totalMem
    }

    /** @throws LocalModelTooLargeException when [model] at [config] needs more than the total RAM. */
    fun check(model: LocalModelDescriptor, config: LocalLoadConfig) {
        val file = model.filePath?.let(::File)?.takeIf { it.isFile } ?: return
        val shape = runCatching { file.inputStream().use { GgufHeaderReader.read(it) }.attention }.getOrNull() ?: return
        val kv = LocalModelMemoryEstimator.kvCacheBytes(shape, config.contextTokens)
        // Q8_0 stores 32 values in 34 bytes, against 64 bytes in F16
        val kvBytes = if (config.quantizedKvCache) kv * Q8_BYTES / F16_BYTES else kv
        val required = file.length() + kvBytes + LocalModelMemoryEstimator.OVERHEAD_BYTES
        if (LocalModelMemoryEstimator.fit(required, mTotalRamBytes) == LocalModelMemoryFit.TooLarge) {
            throw LocalModelTooLargeException()
        }
    }

    private companion object {
        const val Q8_BYTES = 34L
        const val F16_BYTES = 64L
    }
}

/**
 * Keeps the loaded model from hurting the rest of the phone.
 *
 * - When every Himitsu screen is hidden, the model is released after
 *   [LocalInferencePrefs.unloadDelaySeconds]; coming back cancels the timer.
 * - On memory pressure while in the background, or a low-memory signal, it is released at once.
 * - At a critical thermal status the running generation is stopped; what was written is kept.
 * - Releasing waits for a running generation, so a reply is never cut by a timer.
 */
class LocalModelLifecycle(
    private val mApplication: Application,
    private val mRuntime: LocalModelRuntime
) {
    private val mScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mStartedActivities = 0
    private var mUnloadJob: Job? = null

    init {
        mApplication.registerActivityLifecycleCallbacks(ActivityCounter())
        mApplication.registerComponentCallbacks(MemoryCallbacks())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mApplication.getSystemService(PowerManager::class.java)
                .addThermalStatusListener(mApplication.mainExecutor) { status ->
                    if (status >= PowerManager.THERMAL_STATUS_CRITICAL) mRuntime.abortGeneration()
                }
        }
    }

    private fun onAppHidden() {
        val delaySeconds = LocalInferencePrefs.unloadDelaySeconds
        if (delaySeconds == LocalInferencePrefs.KEEP_LOADED) return
        mUnloadJob?.cancel()
        mUnloadJob = mScope.launch {
            delay(delaySeconds * MILLIS_PER_SECOND)
            mRuntime.release()
        }
    }

    private fun onAppVisible() {
        mUnloadJob?.cancel()
        mUnloadJob = null
    }

    private fun releaseNow() {
        mUnloadJob?.cancel()
        mUnloadJob = mScope.launch { mRuntime.release() }
    }

    private inner class ActivityCounter : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            if (mStartedActivities++ == 0) onAppVisible()
        }

        override fun onActivityStopped(activity: Activity) {
            // A configuration change stops and restarts the activity without leaving the app
            if (--mStartedActivities == 0 && !activity.isChangingConfigurations) onAppHidden()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private inner class MemoryCallbacks : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND && mStartedActivities == 0) releaseNow()
        }

        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() = releaseNow()
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
