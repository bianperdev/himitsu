// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GgufHeaderReaderTest {
    @Test
    fun readsTheMetadataThatDescribesAModel() {
        val bytes = gguf {
            string("general.architecture", "llama")
            string("general.name", "Tiny Test")
            uint32("llama.context_length", 8192)
            uint32("general.file_type", 15)
            stringArray("tokenizer.ggml.tokens", listOf("<s>", "</s>", "hello"))
            string("tokenizer.chat_template", "{{ messages }}")
            float32("llama.rope.freq_base", 10000f)
        }

        val metadata = GgufHeaderReader.read(bytes.inputStream())

        assertEquals(3, metadata.version)
        assertEquals("llama", metadata.architecture)
        assertEquals("Tiny Test", metadata.name)
        assertEquals(8192, metadata.trainedContextTokens)
        assertEquals(15, metadata.fileType)
        assertTrue(metadata.hasChatTemplate)
    }

    @Test
    fun toleratesAModelWithoutOptionalKeys() {
        val metadata = GgufHeaderReader.read(gguf { }.inputStream())

        assertNull(metadata.architecture)
        assertNull(metadata.name)
        assertEquals(0, metadata.trainedContextTokens)
        assertFalse(metadata.hasChatTemplate)
        assertEquals(-1, metadata.fileType)
    }

    @Test
    fun rejectsFilesThatAreNotGguf() {
        assertThrows(InvalidGgufException::class.java) {
            GgufHeaderReader.read("PK\u0003\u0004 not a model".toByteArray().inputStream())
        }
    }

    @Test
    fun rejectsATruncatedHeader() {
        val bytes = gguf { string("general.name", "Cut short") }

        assertThrows(InvalidGgufException::class.java) {
            GgufHeaderReader.read(bytes.copyOf(bytes.size - 4).inputStream())
        }
    }

    @Test
    fun rejectsHugeLengthsBeforeAllocatingAnything() {
        val bytes = gguf { rawStringHeader("general.name", Long.MAX_VALUE) }

        assertThrows(InvalidGgufException::class.java) {
            GgufHeaderReader.read(bytes.inputStream())
        }
    }

    @Test
    fun rejectsAnAbsurdMetadataCount() {
        val bytes = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x46554747).putInt(3).putLong(0).putLong(Long.MAX_VALUE).array()

        assertThrows(InvalidGgufException::class.java) {
            GgufHeaderReader.read(bytes.inputStream())
        }
    }

    @Test
    fun acceptsAWellFormedTensorTable() {
        val bytes = gguf {
            tensor("token_embd.weight", listOf(4096L, 32000L), type = 12, offset = 0)
            tensor("output_norm.weight", listOf(4096L), type = 0, offset = 64)
            tensor("scale", emptyList(), type = 0, offset = 128)
        }

        GgufHeaderReader.read(bytes.inputStream(), fileSize = bytes.size + 1024L)
    }

    @Test
    fun rejectsTensorsWithMoreDimensionsThanGgmlAllows() {
        assertInvalid(gguf { tensor("blk.0.attn_q.weight", listOf(1L, 1L, 1L, 1L, 1L)) })
    }

    @Test
    fun rejectsAnUnknownTensorType() {
        assertInvalid(gguf { tensor("blk.0.attn_q.weight", listOf(16L), type = 43) })
    }

    @Test
    fun rejectsAnElementCountThatOverflows() {
        assertInvalid(gguf { tensor("blk.0.attn_q.weight", listOf(Long.MAX_VALUE, 2L)) })
    }

    @Test
    fun rejectsATensorNameLongerThanGgmlAllows() {
        assertInvalid(gguf { tensor("x".repeat(64), listOf(16L)) })
    }

    @Test
    fun rejectsOffsetsThatBreakTheAlignment() {
        assertInvalid(gguf { tensor("blk.0.attn_q.weight", listOf(16L), offset = 7) })
        // 32 is aligned by default, but not to a declared 64-byte alignment
        assertInvalid(gguf {
            uint32("general.alignment", 64)
            tensor("blk.0.attn_q.weight", listOf(16L), offset = 32)
        })
        assertInvalid(gguf { uint32("general.alignment", 48) })
    }

    @Test
    fun rejectsTensorDataOutsideTheFile() {
        val bytes = gguf { tensor("blk.0.attn_q.weight", listOf(16L), offset = 4096) }

        // Without a known size only the header is checked
        GgufHeaderReader.read(bytes.inputStream())
        assertThrows(InvalidGgufException::class.java) {
            GgufHeaderReader.read(bytes.inputStream(), fileSize = bytes.size + 1024L)
        }
    }

    private fun assertInvalid(bytes: ByteArray) {
        assertThrows(InvalidGgufException::class.java) {
            GgufHeaderReader.read(bytes.inputStream())
        }
    }

    /** Builds a minimal GGUF v3 header with the given key-value pairs and tensor descriptions. */
    private fun gguf(block: GgufBuilder.() -> Unit): ByteArray {
        val builder = GgufBuilder().apply(block)
        val head = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x46554747).putInt(3).putLong(builder.tensorCount.toLong()).putLong(builder.count.toLong())
        return head.array() + builder.body.toByteArray() + builder.tensors.toByteArray()
    }

    private class GgufBuilder {
        val body = ByteArrayOutputStream()
        var count = 0
        val tensors = ByteArrayOutputStream()
        var tensorCount = 0

        /** A tensor description; GGUF writes them all after the key-value pairs. */
        fun tensor(name: String, dims: List<Long>, type: Int = 0, offset: Long = 0) {
            tensorCount++
            Writer(tensors).apply {
                putString(name)
                putInt(dims.size)
                dims.forEach { putLong(it) }
                putInt(type)
                putLong(offset)
            }
        }

        fun string(key: String, value: String) = entry(key, 8) { putString(value) }

        fun uint32(key: String, value: Int) = entry(key, 4) { putInt(value) }

        fun float32(key: String, value: Float) = entry(key, 6) { putFloat(value) }

        fun stringArray(key: String, values: List<String>) = entry(key, 9) {
            putInt(8)
            putLong(values.size.toLong())
            values.forEach { putString(it) }
        }

        /** A string entry that only declares its length, to test the bounds checks. */
        fun rawStringHeader(key: String, declaredLength: Long) = entry(key, 8) { putLong(declaredLength) }

        private fun entry(key: String, type: Int, value: Writer.() -> Unit) {
            count++
            Writer(body).apply {
                putString(key)
                putInt(type)
                value()
            }
        }
    }

    private class Writer(private val mOut: ByteArrayOutputStream) {
        fun putInt(value: Int) = mOut.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())

        fun putLong(value: Long) = mOut.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array())

        fun putFloat(value: Float) = mOut.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array())

        fun putString(value: String) {
            val bytes = value.toByteArray()
            putLong(bytes.size.toLong())
            mOut.write(bytes)
        }
    }
}
