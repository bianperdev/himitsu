// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.kafuuneko.rpclient.libs.security.lock.AppLockManager

/** Whether app content may be shown. */
enum class GateState {
    /** Storage is starting; screens show a neutral curtain and wait. */
    Starting,

    /** Content may be shown. */
    Open,

    /** The gate screen must be shown: storage needs a user decision or the app is locked. */
    Blocked
}

/**
 * Starts the gate screen, the only way into the app while the gate is not [GateState.Open].
 *
 * This is the unlock extension point for other tracks: a decoy from `feat/disguise` calls
 * [open] from its secret entry. It only *shows* the gate screen; authentication still happens
 * there, so no entry point can skip it.
 */
fun interface UnlockEntry {
    fun open(context: Context)
}

/**
 * Single source of truth for whether app content may be shown.
 *
 * `CoreActivity` hides its content and opens [UnlockEntry] whenever the state is not
 * [GateState.Open]. The state combines the encrypted storage state and the app lock: storage
 * problems come first, then the lock.
 */
class AppGate(private val mStorage: SecureStorageManager, private val mLock: AppLockManager) {
    private val mScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Current gate state. */
    val state: StateFlow<GateState> = combine(mStorage.state, mLock.isLocked) { storageState, locked -> toGateState(storageState, locked) }
        .stateIn(mScope, SharingStarted.Eagerly, toGateState(mStorage.state.value, mLock.isLocked.value))

    /**
     * The gate state computed right now from its sources, without waiting for [state] to
     * propagate. Pages use it when they return to the screen.
     */
    fun evaluateNow(): GateState = toGateState(mStorage.state.value, mLock.isLocked.value)

    private fun toGateState(storage: SecureStorageState, locked: Boolean): GateState = when (storage) {
        SecureStorageState.Starting -> GateState.Starting
        is SecureStorageState.Failed -> GateState.Blocked
        SecureStorageState.Ready -> if (locked) GateState.Blocked else GateState.Open
    }
}
