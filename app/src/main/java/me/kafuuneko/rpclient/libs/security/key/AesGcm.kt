// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.key

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM sealing with a random 96-bit IV, for keys held in memory.
 *
 * A sealed box is `IV || ciphertext || tag`. Associated data binds a box to its context
 * (file format version, slot type, purpose) so a box cannot be moved to another context.
 */
internal object AesGcm {
    const val KEY_SIZE_BYTES = 32
    const val IV_SIZE_BYTES = 12
    const val TAG_SIZE_BYTES = 16
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** Size of a sealed box that holds [plaintextSize] bytes. */
    fun sealedSize(plaintextSize: Int): Int = IV_SIZE_BYTES + plaintextSize + TAG_SIZE_BYTES

    /** Seals [plaintext] with a raw 256-bit key. */
    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray, random: SecureRandom): ByteArray {
        require(key.size == KEY_SIZE_BYTES) { "AES-256 key expected" }
        val iv = ByteArray(IV_SIZE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BYTES * 8, iv))
        cipher.updateAAD(aad)
        return iv + cipher.doFinal(plaintext)
    }

    /**
     * Opens a box sealed by [seal].
     *
     * @throws GeneralSecurityException when the key, the associated data or the box is wrong
     */
    fun open(key: ByteArray, box: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_SIZE_BYTES) { "AES-256 key expected" }
        return open(SecretKeySpec(key, "AES"), box, aad)
    }

    /** Opens a box with a key object, which may live in the Android Keystore. */
    fun open(key: SecretKey, box: ByteArray, aad: ByteArray): ByteArray {
        if (box.size < IV_SIZE_BYTES + TAG_SIZE_BYTES) throw GeneralSecurityException("Sealed box too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TAG_SIZE_BYTES * 8, box, 0, IV_SIZE_BYTES)
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(box, IV_SIZE_BYTES, box.size - IV_SIZE_BYTES)
    }

    /**
     * Seals with a key object whose provider chooses the IV, as the Android Keystore requires.
     */
    fun seal(key: SecretKey, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        check(iv.size == IV_SIZE_BYTES) { "Unexpected GCM IV size" }
        return iv + ciphertext
    }
}
