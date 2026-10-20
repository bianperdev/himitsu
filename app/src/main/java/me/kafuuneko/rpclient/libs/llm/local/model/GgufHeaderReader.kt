// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local.model

import java.io.DataInputStream
import java.io.EOFException
import java.io.FilterInputStream
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
    val fileType: Int,
    /** Shape of the attention layers, for memory estimates; 0 when the header does not say. */
    val attention: GgufAttentionShape = GgufAttentionShape()
)

/** The parts of a model's shape that decide how much memory its KV cache takes. */
data class GgufAttentionShape(
    val blockCount: Int = 0,
    val headCount: Int = 0,
    /** Key-value heads; models with grouped-query attention have fewer than [headCount]. */
    val headCountKv: Int = 0,
    val embeddingLength: Int = 0,
    /** Size of one key head; 0 means embeddingLength / headCount. */
    val keyLength: Int = 0,
    /** Size of one value head; 0 means the same as the key head. */
    val valueLength: Int = 0
)

/** The file is not a GGUF model this app can read. */
class InvalidGgufException(message: String) : IllegalArgumentException(message)

/**
 * Reads and validates the header of a GGUF file without loading tensor data.
 *
 * GGUF files come from the user's storage and are untrusted. Every length and count is checked
 * against a limit before anything is allocated, and both the key-value section and the tensor
 * table (dimensions, types, offsets, alignment) are validated before the native parser sees the
 * file. The native parser stays the last line of defense: this catches malformed files early,
 * it is not a full proof that a file is safe.
 */
object GgufHeaderReader {
    private const val MAGIC = 0x46554747 // "GGUF" read as a little-endian int
    private const val MAX_KEY_VALUES = 100_000L
    private const val MAX_TENSORS = 1_000_000L
    private const val MAX_STRING_BYTES = 4L * 1024 * 1024
    private const val MAX_ARRAY_ITEMS = 10_000_000L
    private const val MAX_KEPT_STRING_BYTES = 256

    // Limits mirrored from ggml: GGML_MAX_NAME (64, null included), GGML_MAX_DIMS, GGML_TYPE_COUNT
    private const val MAX_TENSOR_NAME_BYTES = 63L
    private const val MAX_TENSOR_DIMS = 4
    private const val GGML_TYPE_COUNT = 43
    private const val DEFAULT_ALIGNMENT = 32L

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
     * @param fileSize size of the whole file when known; tensor offsets are then checked against it.
     * @throws InvalidGgufException when the data is not a supported, well-formed GGUF header.
     */
    fun read(input: InputStream, fileSize: Long = UNKNOWN_SIZE): GgufMetadata {
        val counter = CountingInputStream(input.buffered())
        val data = LittleEndianReader(DataInputStream(counter))
        try {
            // Fixed header: magic, version, tensor and key-value counts
            if (data.int() != MAGIC) throw InvalidGgufException("Not a GGUF file")
            val version = data.int()
            if (version !in 2..3) throw InvalidGgufException("Unsupported GGUF version")
            val tensorCount = data.long()
            if (tensorCount !in 0..MAX_TENSORS) throw InvalidGgufException("Invalid tensor count")
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
            // Tensor table: what the native parser turns into allocations and file offsets
            val alignment = numbers["general.alignment"] ?: DEFAULT_ALIGNMENT
            if (alignment <= 0 || alignment and (alignment - 1) != 0L) throw InvalidGgufException("Invalid alignment")
            val dataOffsets = (0 until tensorCount).map { readTensorInfo(data, alignment) }
            // As in ggml, the data section is only aligned when there are tensors
            if (fileSize != UNKNOWN_SIZE && tensorCount > 0) {
                val dataStart = (counter.position + alignment - 1) / alignment * alignment
                // An empty tensor may sit exactly at the end of the file
                if (dataStart > fileSize || dataOffsets.any { it > fileSize - dataStart }) {
                    throw InvalidGgufException("Tensor data outside the file")
                }
            }

            val architecture = strings["general.architecture"]
            val number = { suffix: String ->
                architecture?.let { numbers["$it.$suffix"] }?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: 0
            }
            return GgufMetadata(
                version = version,
                architecture = architecture,
                name = strings["general.name"]?.takeIf { it.isNotBlank() },
                trainedContextTokens = architecture
                    ?.let { numbers["$it.context_length"] }
                    ?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: 0,
                hasChatTemplate = hasChatTemplate,
                fileType = numbers["general.file_type"]?.toInt() ?: -1,
                attention = GgufAttentionShape(
                    blockCount = number("block_count"),
                    headCount = number("attention.head_count"),
                    headCountKv = number("attention.head_count_kv"),
                    embeddingLength = number("embedding_length"),
                    keyLength = number("attention.key_length"),
                    valueLength = number("attention.value_length")
                )
            )
        } catch (_: EOFException) {
            throw InvalidGgufException("Truncated GGUF header")
        }
    }

    /** Size of a file that is not known, for example while reading from a content URI. */
    const val UNKNOWN_SIZE = -1L

    /** Validates one tensor description and returns its offset in the data section. */
    private fun readTensorInfo(data: LittleEndianReader, alignment: Long): Long {
        data.string(MAX_TENSOR_NAME_BYTES)
        val dims = data.int()
        // Zero dimensions is a scalar, which ggml accepts
        if (dims !in 0..MAX_TENSOR_DIMS) throw InvalidGgufException("Invalid tensor dimensions")
        var elements = 1L
        repeat(dims) {
            val size = data.long()
            if (size < 0) throw InvalidGgufException("Invalid tensor dimensions")
            // Overflowing element counts are what turns a malformed file into a bad allocation
            elements = try {
                Math.multiplyExact(elements, size)
            } catch (_: ArithmeticException) {
                throw InvalidGgufException("Tensor too large")
            }
        }
        val type = data.int()
        if (type !in 0 until GGML_TYPE_COUNT) throw InvalidGgufException("Unknown tensor type")
        val offset = data.long()
        if (offset < 0 || offset % alignment != 0L) throw InvalidGgufException("Invalid tensor offset")
        return offset
    }

    private val INTEGER_TYPES = setOf(
        TYPE_UINT8, TYPE_INT8, TYPE_UINT16, TYPE_INT16, TYPE_UINT32, TYPE_INT32, TYPE_UINT64, TYPE_INT64
    )

    /** Counts the bytes read, to find where the data section starts. */
    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var position = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) position++ }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) position += it }

        override fun skip(count: Long): Long = super.skip(count).also { if (it > 0) position += it }
    }

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
