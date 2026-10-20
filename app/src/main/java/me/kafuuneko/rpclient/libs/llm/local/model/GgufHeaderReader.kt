// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local.model

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream

/** Metadata read from a GGUF header, used to describe and validate an imported model. */
data class GgufMetadata(
    val version: Int,
    val architecture: String?,
    val name: String?,
    /** Context length the model was trained with; 0 when the header does not say. */
    val trainedContextTokens: Int,
    val hasChatTemplate: Boolean,
    /** `general.file_type`, the llama.cpp quantization id; -1 when missing. */
    val fileType: Int
)

/** The file is not a GGUF model this app can read. */
class InvalidGgufException(message: String) : IllegalArgumentException(message)

/**
 * Reads the key-value header of a GGUF file without loading tensors.
 *
 * GGUF files come from the user's storage and are untrusted, so every length and count is
 * checked against a limit before anything is allocated, and the header is fully validated in
 * Kotlin before the native parser ever sees the file.
 */
object GgufHeaderReader {
    private const val MAGIC = 0x46554747 // "GGUF" read as a little-endian int
    private const val MAX_KEY_VALUES = 100_000L
    private const val MAX_TENSORS = 1_000_000L
    private const val MAX_STRING_BYTES = 4L * 1024 * 1024
    private const val MAX_ARRAY_ITEMS = 10_000_000L
    private const val MAX_KEPT_STRING_BYTES = 256

    private const val TYPE_UINT8 = 0
    private const val TYPE_INT8 = 1
    private const val TYPE_UINT16 = 2
    private const val TYPE_INT16 = 3
    private const val TYPE_UINT32 = 4
    private const val TYPE_INT32 = 5
    private const val TYPE_FLOAT32 = 6
    private const val TYPE_BOOL = 7
    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9
    private const val TYPE_UINT64 = 10
    private const val TYPE_INT64 = 11
    private const val TYPE_FLOAT64 = 12

    /**
     * Reads the header from [input], which must be at the start of the file.
     *
     * @throws InvalidGgufException when the data is not a supported GGUF header.
     */
    fun read(input: InputStream): GgufMetadata {
        val data = LittleEndianReader(DataInputStream(input.buffered()))
        try {
            // Fixed header: magic, version, tensor and key-value counts
            if (data.int() != MAGIC) throw InvalidGgufException("Not a GGUF file")
            val version = data.int()
            if (version !in 2..3) throw InvalidGgufException("Unsupported GGUF version")
            if (data.long() !in 0..MAX_TENSORS) throw InvalidGgufException("Invalid tensor count")
            val keyValues = data.long()
            if (keyValues !in 0..MAX_KEY_VALUES) throw InvalidGgufException("Invalid metadata count")

            // Key-value pairs: keep the few that describe the model, skip the rest
            val strings = mutableMapOf<String, String>()
            val numbers = mutableMapOf<String, Long>()
            var hasChatTemplate = false
            repeat(keyValues.toInt()) {
                val key = data.string(MAX_KEPT_STRING_BYTES.toLong())
                val type = data.int()
                when {
                    type == TYPE_STRING && key == "tokenizer.chat_template" -> {
                        hasChatTemplate = data.skipString() > 0
                    }
                    type == TYPE_STRING && (key == "general.architecture" || key == "general.name") -> {
                        strings[key] = data.string(MAX_STRING_BYTES).take(MAX_KEPT_STRING_BYTES)
                    }
                    type in INTEGER_TYPES -> numbers[key] = data.integer(type)
                    else -> data.skipValue(type)
                }
            }
            val architecture = strings["general.architecture"]
            return GgufMetadata(
                version = version,
                architecture = architecture,
                name = strings["general.name"]?.takeIf { it.isNotBlank() },
                trainedContextTokens = architecture
                    ?.let { numbers["$it.context_length"] }
                    ?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: 0,
                hasChatTemplate = hasChatTemplate,
                fileType = numbers["general.file_type"]?.toInt() ?: -1
            )
        } catch (_: EOFException) {
            throw InvalidGgufException("Truncated GGUF header")
        }
    }

    private val INTEGER_TYPES = setOf(
        TYPE_UINT8, TYPE_INT8, TYPE_UINT16, TYPE_INT16, TYPE_UINT32, TYPE_INT32, TYPE_UINT64, TYPE_INT64
    )

    /** Little-endian primitive reads with bounds checks on every variable-length field. */
    private class LittleEndianReader(private val mInput: DataInputStream) {
        fun byte(): Int = mInput.readUnsignedByte()

        fun int(): Int = Integer.reverseBytes(mInput.readInt())

        fun long(): Long = java.lang.Long.reverseBytes(mInput.readLong())

        fun string(maxBytes: Long): String {
            val length = long()
            if (length !in 0..maxBytes) throw InvalidGgufException("String too long")
            val bytes = ByteArray(length.toInt())
            mInput.readFully(bytes)
            return String(bytes, Charsets.UTF_8)
        }

        /** Skips a string and returns its length. */
        fun skipString(): Long {
            val length = long()
            if (length !in 0..MAX_STRING_BYTES) throw InvalidGgufException("String too long")
            skip(length)
            return length
        }

        fun integer(type: Int): Long = when (type) {
            TYPE_UINT8 -> byte().toLong()
            TYPE_INT8 -> byte().toByte().toLong()
            TYPE_UINT16 -> (byte() or (byte() shl 8)).toLong()
            TYPE_INT16 -> (byte() or (byte() shl 8)).toShort().toLong()
            TYPE_UINT32 -> int().toLong() and 0xFFFF_FFFFL
            TYPE_INT32 -> int().toLong()
            else -> long()
        }

        fun skipValue(type: Int) {
            when (type) {
                TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> skip(1)
                TYPE_UINT16, TYPE_INT16 -> skip(2)
                TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> skip(4)
                TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> skip(8)
                TYPE_STRING -> skipString()
                TYPE_ARRAY -> skipArray()
                else -> throw InvalidGgufException("Unknown metadata type")
            }
        }

        private fun skipArray() {
            val itemType = int()
            val count = long()
            if (count !in 0..MAX_ARRAY_ITEMS) throw InvalidGgufException("Array too long")
            // Nested arrays are not used by GGUF metadata; refusing them bounds the recursion
            if (itemType == TYPE_ARRAY) throw InvalidGgufException("Nested arrays are not supported")
            when (itemType) {
                TYPE_STRING -> repeat(count.toInt()) { skipString() }
                TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> skip(count)
                TYPE_UINT16, TYPE_INT16 -> skip(count * 2)
                TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> skip(count * 4)
                TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> skip(count * 8)
                else -> throw InvalidGgufException("Unknown array type")
            }
        }

        private fun skip(bytes: Long) {
            var remaining = bytes
            while (remaining > 0) {
                val skipped = mInput.skip(remaining)
                if (skipped <= 0) {
                    // skip() may return 0 before the end; a read tells EOF apart
                    if (mInput.read() < 0) throw EOFException()
                    remaining--
                } else {
                    remaining -= skipped
                }
            }
        }
    }
}
