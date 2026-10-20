// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SqlCipherKeyTest {
    @Test
    fun rawKeyUsesSqlCipherHexBlobSyntax() {
        val key = ByteArray(32) { it.toByte() }

        val passphrase = SqlCipherKey.rawKeyPassphrase(key)

        val expected = "x'000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F'"
        assertTrue(passphrase.contentEquals(expected.toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun onlyThirtyTwoByteKeysAreAccepted() {
        assertThrows(IllegalArgumentException::class.java) { SqlCipherKey.rawKeyPassphrase(ByteArray(16)) }
        assertEquals(67, SqlCipherKey.rawKeyPassphrase(ByteArray(32)).size)
    }
}
