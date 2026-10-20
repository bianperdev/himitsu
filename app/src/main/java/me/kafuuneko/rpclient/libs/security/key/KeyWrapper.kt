// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.key

/** Why a hardware-backed wrapping key could not be used. */
enum class KeyWrapperFailure {
    /** The wrapping key no longer exists or no longer matches the data. Retrying will not help. */
    KeyLost,

    /** The key store failed for another reason, which may be temporary. */
    Unavailable
}

/** A [KeyWrapper] operation failed. The message never contains key material. */
class KeyWrapperException(val failure: KeyWrapperFailure, cause: Throwable? = null) :
    Exception("Key wrapper failure: $failure", cause)

/**
 * Seals small secrets with keys that never leave secure hardware.
 *
 * Each key is named by a key ID stored next to the sealed data. A new master key always gets a
 * new key ID, and keys are never deleted by the vault, so data set aside by "start over" can
 * still be opened by its own key. The production implementation is [AndroidKeystoreKeyWrapper];
 * unit tests use an in-memory implementation.
 */
interface KeyWrapper {
    /**
     * Seals [plaintext] with the key [keyId], creating that key if it does not exist yet.
     *
     * @throws KeyWrapperException when the key store cannot be used
     */
    fun seal(keyId: String, plaintext: ByteArray, aad: ByteArray): ByteArray

    /**
     * Opens a box created by [seal] with the same [keyId].
     *
     * @throws KeyWrapperException with [KeyWrapperFailure.KeyLost] when the key is gone or does
     *   not match, or [KeyWrapperFailure.Unavailable] for other failures
     */
    fun open(keyId: String, box: ByteArray, aad: ByteArray): ByteArray
}
