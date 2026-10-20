// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.key

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.BufferUnderflowException

/** Kind of key slot. The [id] is part of the on-disk format. */
enum class KeySlotType(val id: Int) {
    /** Master key sealed with an AES-GCM key held in the Android Keystore. */
    Keystore(1),

    /** Master key sealed with a key derived from the user's recovery code. */
    RecoveryCode(2);

    companion object {
        fun fromId(id: Int): KeySlotType? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One wrapped copy of the master key.
 *
 * @property salt random salt for the key derivation; empty for slots that do not derive their key
 * @property sealedKey `IV || ciphertext || tag` of the master key
 */
class KeySlot(val type: KeySlotType, val salt: ByteArray, val sealedKey: ByteArray) {
    override fun toString(): String = "KeySlot(type=$type)"
}

/** Content of the master key file: the wrapped copies of the master key. */
data class MasterKeyFile(val slots: List<KeySlot>) {
    /** The slot of [type], or null when the user has not configured it. */
    fun slot(type: KeySlotType): KeySlot? = slots.firstOrNull { it.type == type }

    /** A copy of this file where the slot of the same type is replaced by [slot]. */
    fun withSlot(slot: KeySlot): MasterKeyFile =
        MasterKeyFile(slots.filterNot { it.type == slot.type } + slot)

    /** A copy of this file without the slot of [type]. */
    fun withoutSlot(type: KeySlotType): MasterKeyFile =
        MasterKeyFile(slots.filterNot { it.type == type })
}

/** The master key file cannot be parsed: it is truncated, damaged or from an unknown version. */
class MasterKeyFileFormatException(message: String) : Exception(message)

/**
 * Binary codec for [MasterKeyFile].
 *
 * Layout, all integers big-endian:
 * - magic `HMKY`, format version (1 byte), slot count (1 byte)
 * - per slot: type (1 byte), salt length (1 byte), salt, sealed key length (2 bytes), sealed key
 *
 * Parsing is strict: unknown versions, unknown slot types, duplicate slots and trailing bytes
 * are rejected, so a damaged file is reported instead of being half-read.
 */
internal object MasterKeyFileCodec {
    const val FORMAT_VERSION = 1
    private val MAGIC = byteArrayOf('H'.code.toByte(), 'M'.code.toByte(), 'K'.code.toByte(), 'Y'.code.toByte())
    private const val MAX_SALT = 64
    private const val MAX_SEALED = 256

    /**
     * Associated data for sealing a slot of [type], binding it to the file format version.
     */
    fun slotAad(type: KeySlotType): ByteArray = MAGIC + byteArrayOf(FORMAT_VERSION.toByte(), type.id.toByte())

    fun encode(file: MasterKeyFile): ByteArray {
        require(file.slots.size in 1..255) { "A master key file needs at least one slot" }
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(FORMAT_VERSION)
        out.write(file.slots.size)
        file.slots.forEach { slot ->
            require(slot.salt.size <= MAX_SALT && slot.sealedKey.size <= MAX_SEALED) { "Slot too large" }
            out.write(slot.type.id)
            out.write(slot.salt.size)
            out.write(slot.salt)
            out.write(slot.sealedKey.size shr 8)
            out.write(slot.sealedKey.size and 0xFF)
            out.write(slot.sealedKey)
        }
        return out.toByteArray()
    }

    /**
     * Parses a master key file.
     *
     * @throws MasterKeyFileFormatException when the content is not a valid master key file
     */
    fun decode(bytes: ByteArray): MasterKeyFile {
        val buffer = ByteBuffer.wrap(bytes)
        try {
            // Header
            val magic = ByteArray(MAGIC.size).also(buffer::get)
            if (!magic.contentEquals(MAGIC)) throw MasterKeyFileFormatException("Not a master key file")
            val version = buffer.get().toInt() and 0xFF
            if (version != FORMAT_VERSION) throw MasterKeyFileFormatException("Unsupported format version $version")
            val count = buffer.get().toInt() and 0xFF
            if (count == 0) throw MasterKeyFileFormatException("No key slot")
            // Slots
            val slots = List(count) { readSlot(buffer) }
            if (slots.distinctBy { it.type }.size != slots.size) {
                throw MasterKeyFileFormatException("Duplicate key slot")
            }
            if (buffer.hasRemaining()) throw MasterKeyFileFormatException("Trailing data")
            return MasterKeyFile(slots)
        } catch (_: BufferUnderflowException) {
            throw MasterKeyFileFormatException("Truncated master key file")
        }
    }

    private fun readSlot(buffer: ByteBuffer): KeySlot {
        val typeId = buffer.get().toInt() and 0xFF
        val type = KeySlotType.fromId(typeId) ?: throw MasterKeyFileFormatException("Unknown slot type $typeId")
        val saltLength = buffer.get().toInt() and 0xFF
        if (saltLength > MAX_SALT) throw MasterKeyFileFormatException("Salt too long")
        val salt = ByteArray(saltLength).also(buffer::get)
        val sealedLength = buffer.getShort().toInt() and 0xFFFF
        if (sealedLength > MAX_SEALED) throw MasterKeyFileFormatException("Sealed key too long")
        val sealed = ByteArray(sealedLength).also(buffer::get)
        return KeySlot(type, salt, sealed)
    }
}
