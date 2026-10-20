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
 * Seals small secrets with a key that never leaves secure hardware.
 *
 * The production implementation is [AndroidKeystoreKeyWrapper]; unit tests use an in-memory
 * implementation so the vault logic can be tested without the Keystore.
 */
interface KeyWrapper {
    /**
     * Seals [plaintext], creating the wrapping key if it does not exist yet.
     *
     * @throws KeyWrapperException when the key store cannot be used
     */
    fun seal(plaintext: ByteArray, aad: ByteArray): ByteArray

    /**
     * Opens a box created by [seal].
     *
     * @throws KeyWrapperException with [KeyWrapperFailure.KeyLost] when the wrapping key is gone
     *   or does not match, or [KeyWrapperFailure.Unavailable] for other failures
     */
    fun open(box: ByteArray, aad: ByteArray): ByteArray

    /**
     * Deletes the wrapping key and creates a new one on the next [seal].
     *
     * Only called when no data depends on the old key: a fresh install, or after the master key
     * was recovered and is about to be sealed again.
     */
    fun resetKey()
}
