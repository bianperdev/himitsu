// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.key

import java.security.SecureRandom

/**
 * A random recovery code that can unwrap the master key when the Keystore key is lost.
 *
 * - 28 symbols of Crockford base32, which is 140 bits of entropy. That is far beyond guessing
 *   range, so the wrapping key is derived with HKDF instead of a slow password KDF.
 * - Shown grouped as `XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX`. Input is case-insensitive, ignores
 *   spaces and dashes, and accepts the usual confusions (`O` for `0`, `I` and `L` for `1`).
 * - The code is a secret: it is never logged and [toString] hides it.
 */
class RecoveryCode private constructor(private val mSymbols: CharArray) {

    /** The code grouped for display. Only for the screen that shows it to the user once. */
    fun toDisplayString(): String =
        mSymbols.toList().chunked(GROUP_SIZE).joinToString("-") { it.joinToString("") }

    /** Input keying material for the recovery slot. The caller should wipe it after use. */
    internal fun keyMaterial(): ByteArray = ByteArray(mSymbols.size) { mSymbols[it].code.toByte() }

    /** Wipes the code from memory. */
    fun wipe() {
        mSymbols.fill(' ')
    }

    override fun toString(): String = "RecoveryCode([REDACTED])"

    companion object {
        const val LENGTH = 28
        private const val GROUP_SIZE = 4
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

        /** Generates a new random code. */
        fun generate(random: SecureRandom): RecoveryCode =
            RecoveryCode(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

        /**
         * Parses a code typed by the user.
         *
         * @return the code, or null when it has the wrong length or an invalid symbol
         */
        fun parse(input: CharSequence): RecoveryCode? {
            val symbols = CharArray(LENGTH)
            var count = 0
            for (raw in input) {
                if (raw == '-' || raw.isWhitespace()) continue
                val symbol = normalize(raw) ?: return null
                if (count == LENGTH) return null
                symbols[count++] = symbol
            }
            return if (count == LENGTH) RecoveryCode(symbols) else null
        }

        private fun normalize(raw: Char): Char? = when (val upper = raw.uppercaseChar()) {
            'O' -> '0'
            'I', 'L' -> '1'
            else -> upper.takeIf { it in ALPHABET }
        }
    }
}
