// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.key

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF with HMAC-SHA256, as specified in RFC 5869.
 *
 * Used to derive independent keys from one high-entropy secret (the master key or a recovery
 * code), so that a key is never reused for two purposes.
 */
internal object Hkdf {
    private const val ALGORITHM = "HmacSHA256"
    private const val HASH_LENGTH = 32

    /**
     * Derives [length] bytes of output keying material.
     *
     * @param ikm input keying material; it must already be high-entropy, HKDF does not slow down guessing
     * @param salt optional salt; an empty salt is replaced by zeros as the RFC requires
     * @param info context string that separates the purposes of the derived keys
     * @param length number of bytes to derive, at most 255 times the hash length
     * @return the derived key; the caller owns it and should wipe it after use
     */
    fun deriveSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..255 * HASH_LENGTH) { "Invalid HKDF output length" }
        val prk = extract(salt, ikm)
        try {
            return expand(prk, info, length)
        } finally {
            prk.fill(0)
        }
    }

    private fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val effectiveSalt = if (salt.isEmpty()) ByteArray(HASH_LENGTH) else salt
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(effectiveSalt, ALGORITHM))
        return mac.doFinal(ikm)
    }

    private fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(prk, ALGORITHM))
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            val block = mac.doFinal()
            previous.fill(0)
            val count = minOf(block.size, length - offset)
            System.arraycopy(block, 0, output, offset, count)
            offset += count
            counter++
            previous = block
        }
        previous.fill(0)
        return output
    }
}
