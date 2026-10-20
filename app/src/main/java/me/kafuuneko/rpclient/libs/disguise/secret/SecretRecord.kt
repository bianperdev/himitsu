// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise.secret

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import me.kafuuneko.rpclient.libs.disguise.Appearance

/**
 * What is kept about the disguise shortcut: a salted PBKDF2 hash, never the shortcut itself.
 *
 * The shortcut only leads to the app lock, so it is a discretion secret, not a security
 * boundary. A short numeric code can still be brute-forced by someone who reads this record;
 * the hash keeps it from being read at a glance and makes every guess costly.
 *
 * The derived input is `<appearance id>:<shortcut>`, so the same digits never open another decoy.
 */
class SecretRecord(
    val appearanceId: String,
    val iterations: Int,
    private val mSalt: ByteArray,
    private val mHash: ByteArray
) {
    val salt: ByteArray
        get() = mSalt.copyOf()

    val hash: ByteArray
        get() = mHash.copyOf()

    /** Whether [candidate] is the shortcut of [appearance]. Compares in constant time. */
    fun matches(appearance: Appearance, candidate: CharArray): Boolean {
        if (appearance.id != appearanceId) return false
        val derived = derive(appearance, candidate, mSalt, iterations)
        return try {
            MessageDigest.isEqual(derived, mHash)
        } finally {
            derived.fill(0)
        }
    }

    override fun toString(): String = "SecretRecord([REDACTED])"

    companion object {
        /**
         * Half the OWASP 2023 recommendation for PBKDF2-HMAC-SHA256 (600,000): that one took 2.1 s
         * on the reference emulator, too slow for a shortcut. Records keep their own count, so
         * it can be raised later without breaking stored shortcuts.
         */
        const val DEFAULT_ITERATIONS = 310_000

        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256

        /** A record for [secret] with a fresh random salt. [secret] is not modified. */
        fun create(
            appearance: Appearance,
            secret: CharArray,
            random: SecureRandom,
            iterations: Int = DEFAULT_ITERATIONS
        ): SecretRecord {
            val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
            return SecretRecord(appearance.id, iterations, salt, derive(appearance, secret, salt, iterations))
        }

        /** PBKDF2-HMAC-SHA256; every temporary copy of the input is wiped. */
        internal fun pbkdf2(input: CharArray, salt: ByteArray, iterations: Int, bits: Int = HASH_BITS): ByteArray {
            val spec = PBEKeySpec(input, salt, iterations, bits)
            try {
                return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        }

        private fun derive(appearance: Appearance, input: CharArray, salt: ByteArray, iterations: Int): ByteArray {
            val normalized = "${appearance.id}:".toCharArray() + input
            try {
                return pbkdf2(normalized, salt, iterations)
            } finally {
                normalized.fill('\u0000')
            }
        }
    }
}
