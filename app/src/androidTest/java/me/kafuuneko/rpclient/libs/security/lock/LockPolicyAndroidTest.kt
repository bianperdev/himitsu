// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.lock

import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Checks that a stricter [LockPolicy] locks the app at once, whatever the timeout, and that
 * `disguiseModule` replaces the default policy. The lock preferences are restored afterwards.
 */
@RunWith(AndroidJUnit4::class)
class LockPolicyAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var savedEnabled = false
    private var savedTimeout = ""

    @Before
    fun saveLockPrefs() {
        savedEnabled = SecurityPrefs.appLockEnabled
        savedTimeout = SecurityPrefs.lockTimeout
    }

    @After
    fun restoreLockPrefs() {
        SecurityPrefs.appLockEnabled = savedEnabled
        SecurityPrefs.lockTimeout = savedTimeout
    }

    @Test
    fun stricterPolicyLocksWithinTheTimeout() {
        assertTrue(leaveAndReturn(LockPolicy { true }))
    }

    @Test
    fun defaultPolicyWaitsForTheTimeout() {
        assertFalse(leaveAndReturn(LockPolicy.Default))
    }

    @Test
    fun disguiseModuleReplacesTheDefaultPolicy() {
        assertNotSame(LockPolicy.Default, GlobalContext.get().get<LockPolicy>())
    }

    /** Leaves and comes back right away with a 5-minute timeout; returns whether the app locked. */
    private fun leaveAndReturn(policy: LockPolicy): Boolean {
        // Created with the lock off, so it starts unlocked without any authentication
        SecurityPrefs.appLockEnabled = false
        val manager = AppLockManager(context, policy)
        SecurityPrefs.appLockEnabled = true
        manager.timeout = LockTimeout.FiveMinutes
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val owner = ProcessLifecycleOwner.get()
            manager.onStop(owner)
            manager.onStart(owner)
        }
        return manager.isLocked.value
    }
}
