// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository

/**
 * Keeps debug request logs short-lived (P-07).
 *
 * - Turning debug mode off deletes every stored log at once.
 * - At startup, logs are deleted when debug mode is off, and pruned to the retention limits
 *   when it is on.
 *
 * It listens to the preference itself, so the inherited settings screen needs no change.
 */
class RequestLogGuard(private val mRepository: LLMRequestLogRepository) {
    private val mScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // SharedPreferences keeps listeners weakly, so this instance holds a strong reference
    private val mListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == DEBUG_MODE_KEY && !AppModel.debugModeEnabled) {
            mScope.launch { mRepository.deleteAll() }
        }
    }

    /** Applies the policy now and watches for debug mode being turned off. Call once, after storage is ready. */
    suspend fun start() {
        AppModel.preferences.registerOnSharedPreferenceChangeListener(mListener)
        if (AppModel.debugModeEnabled) mRepository.pruneExpired() else mRepository.deleteAll()
    }

    private companion object {
        /** Kotpref stores a property under its own name. */
        const val DEBUG_MODE_KEY = "debugModeEnabled"
    }
}
