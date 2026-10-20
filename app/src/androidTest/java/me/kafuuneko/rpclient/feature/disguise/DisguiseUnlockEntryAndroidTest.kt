// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise

import androidx.test.ext.junit.runners.AndroidJUnit4
import me.kafuuneko.rpclient.libs.security.UnlockEntry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * The disguise module must override the security module's [UnlockEntry]; if the module order
 * changed, a locked page would show the Himitsu gate screen instead of the decoy.
 */
@RunWith(AndroidJUnit4::class)
class DisguiseUnlockEntryAndroidTest {
    @Test
    fun koinResolvesTheDisguiseUnlockEntry() {
        assertTrue(GlobalContext.get().get<UnlockEntry>() is DisguiseUnlockEntry)
    }
}
