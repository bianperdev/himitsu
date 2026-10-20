// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.key

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterKeyFileCodecTest {
    private val keystoreSlot = KeySlot(KeySlotType.Keystore, ByteArray(0), ByteArray(60) { it.toByte() })
    private val recoverySlot = KeySlot(KeySlotType.RecoveryCode, ByteArray(16) { 7 }, ByteArray(60) { 3 })

    @Test
    fun encodedFileDecodesToTheSameSlots() {
        val decoded = MasterKeyFileCodec.decode(
            MasterKeyFileCodec.encode(MasterKeyFile(listOf(keystoreSlot, recoverySlot)))
        )

        assertEquals(listOf(KeySlotType.Keystore, KeySlotType.RecoveryCode), decoded.slots.map { it.type })
        val recovery = decoded.slot(KeySlotType.RecoveryCode)!!
        assertTrue(recovery.salt.contentEquals(recoverySlot.salt))
        assertTrue(recovery.sealedKey.contentEquals(recoverySlot.sealedKey))
    }

    @Test
    fun damagedFilesAreRejectedInsteadOfHalfRead() {
        val valid = MasterKeyFileCodec.encode(MasterKeyFile(listOf(keystoreSlot)))
        val wrongMagic = valid.copyOf().also { it[0] = 'X'.code.toByte() }
        val futureVersion = valid.copyOf().also { it[4] = 2 }
        val unknownSlot = valid.copyOf().also { it[6] = 9 }
        val truncated = valid.copyOf(valid.size - 1)
        val trailing = valid + byteArrayOf(0)

        listOf(wrongMagic, futureVersion, unknownSlot, truncated, trailing, ByteArray(0)).forEach { bytes ->
            assertThrows(MasterKeyFileFormatException::class.java) { MasterKeyFileCodec.decode(bytes) }
        }
    }

    @Test
    fun duplicateSlotsAreRejected() {
        val bytes = MasterKeyFileCodec.encode(MasterKeyFile(listOf(keystoreSlot, keystoreSlot)))

        assertThrows(MasterKeyFileFormatException::class.java) { MasterKeyFileCodec.decode(bytes) }
    }
}
