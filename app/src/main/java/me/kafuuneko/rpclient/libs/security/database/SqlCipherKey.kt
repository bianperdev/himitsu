// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.database

/**
 * Formats keys for SQLCipher.
 *
 * A 256-bit key written as `x'<64 hex digits>'` is used as the raw encryption key, which skips
 * SQLCipher's PBKDF2. That is safe because the key is random, not a password, and it makes
 * opening the database fast. The result is built as bytes, never as a `String`, so it can be
 * wiped after use.
 */
internal object SqlCipherKey {
    private const val RAW_KEY_SIZE = 32
    private val HEX = "0123456789ABCDEF".toByteArray(Charsets.US_ASCII)

    /**
     * Returns the raw-key passphrase for a 32-byte [key]. The caller owns the result.
     */
    fun rawKeyPassphrase(key: ByteArray): ByteArray {
        require(key.size == RAW_KEY_SIZE) { "Raw SQLCipher keys are 32 bytes" }
        val out = ByteArray(3 + RAW_KEY_SIZE * 2)
        out[0] = 'x'.code.toByte()
        out[1] = '\''.code.toByte()
        key.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            out[2 + index * 2] = HEX[value ushr 4]
            out[3 + index * 2] = HEX[value and 0x0F]
        }
        out[out.size - 1] = '\''.code.toByte()
        return out
    }
}
