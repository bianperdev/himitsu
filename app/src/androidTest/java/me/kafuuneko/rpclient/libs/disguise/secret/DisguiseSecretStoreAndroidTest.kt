// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise.secret

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.SecureRandom
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.disguise.Appearance
import me.kafuuneko.rpclient.libs.disguise.assumeEmulator
import me.kafuuneko.rpclient.libs.disguise.isEmulator
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the shortcut store on a device, with the real PBKDF2 cost and preferences file. */
@RunWith(AndroidJUnit4::class)
class DisguiseSecretStoreAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var now = 1_000_000L
    private val store = DisguiseSecretStore(context, SecureRandom()) { now }

    @Before
    fun onlyOnEmulator() = assumeEmulator()

    @After
    fun clear() {
        if (!isEmulator()) return
        store.clear()
    }

    @Test
    fun theStoredShortcutOpensOnlyItsOwnDecoy() = runBlocking {
        store.set(Appearance.Calculator, "482913".toCharArray())
        assertTrue(store.isSet(Appearance.Calculator))
        assertFalse(store.isSet(Appearance.Compass))
        assertTrue(store.verify(Appearance.Calculator, "482913".toCharArray()))
        assertFalse(store.verify(Appearance.Calculator, "482914".toCharArray()))
        assertFalse(store.verify(Appearance.Compass, "482913".toCharArray()))
    }

    @Test
    fun wrongAttemptsSilentlyBlockEvenTheRightShortcutUntilTheWaitEnds() = runBlocking {
        store.set(Appearance.Calculator, "482913".toCharArray())
        repeat(AttemptLimiter.FREE_ATTEMPTS) { store.verify(Appearance.Calculator, "000000".toCharArray()) }
        assertFalse(store.verify(Appearance.Calculator, "482913".toCharArray()))
        now += 30_000L
        assertTrue(store.verify(Appearance.Calculator, "482913".toCharArray()))
    }

    @Test
    fun shortcutsPassedInAreWiped() = runBlocking {
        val secret = "482913".toCharArray()
        store.set(Appearance.Calculator, secret)
        val candidate = "482913".toCharArray()
        store.verify(Appearance.Calculator, candidate)
        assertArrayEquals(CharArray(6), secret)
        assertArrayEquals(CharArray(6), candidate)
    }
}
