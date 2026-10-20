// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.key

import java.security.SecureRandom

/**
 * What a derived key is used for. Each purpose gets an independent key from the master key.
 *
 * The [info] strings are part of the on-disk format: changing one makes the data it protects
 * unreadable.
 */
enum class KeyPurpose(val info: String) {
    PrimaryDatabase("himitsu/v1/database/primary"),
    RequestLogDatabase("himitsu/v1/database/request-log"),
    LocalModelDatabase("himitsu/v1/database/local-models"),
    Preferences("himitsu/v1/preferences"),
    Files("himitsu/v1/files")
}

/**
 * The random 256-bit root key of Himitsu's encrypted storage.
 *
 * It is never stored in plain form: [MasterKeyVault] keeps it wrapped in key slots. Callers
 * receive derived keys for one [KeyPurpose] at a time, never the raw master key.
 */
class MasterKey private constructor(private val mBytes: ByteArray) {

    /**
     * Derives the 256-bit key for [purpose].
     *
     * @return a new array that the caller owns and should wipe after use
     */
    fun derive(purpose: KeyPurpose): ByteArray =
        Hkdf.deriveSha256(mBytes, ByteArray(0), purpose.info.toByteArray(Charsets.UTF_8), SIZE_BYTES)

    /** Returns a copy of the raw key, only for wrapping it into a key slot. */
    internal fun copyBytes(): ByteArray = mBytes.copyOf()

    /** Wipes the key from memory. The instance must not be used afterwards. */
    fun destroy() {
        mBytes.fill(0)
    }

    override fun toString(): String = "MasterKey([REDACTED])"

    companion object {
        const val SIZE_BYTES = 32

        /** Generates a new random master key. */
        fun generate(random: SecureRandom): MasterKey =
            MasterKey(ByteArray(SIZE_BYTES).also(random::nextBytes))

        /** Takes ownership of [bytes], which must not be used or wiped by the caller afterwards. */
        internal fun adopt(bytes: ByteArray): MasterKey {
            require(bytes.size == SIZE_BYTES) { "Invalid master key size" }
            return MasterKey(bytes)
        }
    }
}
