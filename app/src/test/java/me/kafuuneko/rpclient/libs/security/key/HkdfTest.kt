// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.key

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks HKDF against the published RFC 5869 test vectors. Results are compared with
 * `contentEquals` so that a failure never prints key bytes.
 */
class HkdfTest {
    @Test
    fun matchesRfc5869TestCase1() {
        val okm = Hkdf.deriveSha256(
            ikm = ByteArray(22) { 0x0b },
            salt = hex("000102030405060708090a0b0c"),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            length = 42
        )

        assertTrue(okm.contentEquals(hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865")))
    }

    @Test
    fun matchesRfc5869TestCase3WithEmptySaltAndInfo() {
        val okm = Hkdf.deriveSha256(
            ikm = ByteArray(22) { 0x0b },
            salt = ByteArray(0),
            info = ByteArray(0),
            length = 42
        )

        assertTrue(okm.contentEquals(hex("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8")))
    }

    private fun hex(value: String): ByteArray =
        ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
