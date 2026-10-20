// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.files

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.SecureRandom
import me.kafuuneko.rpclient.libs.security.files.FileCipher.Companion.HEADER_SIZE
import me.kafuuneko.rpclient.libs.security.files.FileCipher.Companion.SEGMENT_SIZE
import me.kafuuneko.rpclient.libs.security.files.FileCipher.Companion.TAG_SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Uses random placeholder bytes only. */
class FileCipherTest {
    private val random = SecureRandom()
    private val cipher = FileCipher.create(ByteArray(32).also(random::nextBytes), random)

    @Test
    fun contentRoundTripsAtSegmentBoundaries() {
        for (size in listOf(0, 1, SEGMENT_SIZE - 1, SEGMENT_SIZE, SEGMENT_SIZE + 1, 3 * SEGMENT_SIZE, 3 * SEGMENT_SIZE + 17)) {
            val plain = bytes(size)
            val sealed = seal(plain)

            assertEquals(FileCipher.sealedLength(size.toLong()), sealed.size.toLong())
            assertEquals(size.toLong(), FileCipher.plainLength(sealed.size.toLong()))
            assertTrue("size $size", open(sealed).contentEquals(plain))
        }
    }

    @Test
    fun theSealedFileDoesNotContainThePlaintext() {
        val plain = ByteArray(4096) { 0x41 }
        val sealed = seal(plain)

        assertEquals("HMF1", String(sealed, 0, 4, Charsets.US_ASCII))
        assertFalse(sealed.toList().windowed(64).any { window -> window.all { it == 0x41.toByte() } })
    }

    @Test
    fun aChangedByteIsDetected() {
        val sealed = seal(bytes(2 * SEGMENT_SIZE))
        for (position in listOf(5, HEADER_SIZE, HEADER_SIZE + SEGMENT_SIZE + TAG_SIZE + 3, sealed.size - 1)) {
            val tampered = sealed.copyOf()
            tampered[position] = (tampered[position].toInt() xor 1).toByte()
            assertDoesNotOpen(tampered)
        }
    }

    @Test
    fun truncationAtASegmentBoundaryIsDetected() {
        val sealed = seal(bytes(3 * SEGMENT_SIZE))

        assertDoesNotOpen(sealed.copyOf(HEADER_SIZE + 2 * (SEGMENT_SIZE + TAG_SIZE)))
        assertDoesNotOpen(sealed.copyOf(HEADER_SIZE + SEGMENT_SIZE + TAG_SIZE))
    }

    @Test
    fun reorderedSegmentsAreDetected() {
        val sealed = seal(bytes(3 * SEGMENT_SIZE))
        val segment = SEGMENT_SIZE + TAG_SIZE
        val reordered = sealed.copyOf()
        System.arraycopy(sealed, HEADER_SIZE, reordered, HEADER_SIZE + segment, segment)
        System.arraycopy(sealed, HEADER_SIZE + segment, reordered, HEADER_SIZE, segment)

        assertDoesNotOpen(reordered)
    }

    @Test
    fun anAppendedSegmentIsDetected() {
        val sealed = seal(bytes(SEGMENT_SIZE))
        val extended = sealed + sealed.copyOfRange(HEADER_SIZE, sealed.size)

        assertDoesNotOpen(extended)
    }

    @Test
    fun anotherKeyCannotOpenTheFile() {
        val sealed = seal(bytes(100))
        val other = FileCipher.create(ByteArray(32).also(random::nextBytes), random)

        try {
            other.open(ByteArrayInputStream(sealed), sealed.size.toLong()).use { it.readBytes() }
            fail("Opened with another key")
        } catch (_: SealedFileException) {
        }
    }

    @Test
    fun aFailedWriteLeavesAFileThatNeverOpens() {
        val output = ByteArrayOutputStream()
        try {
            cipher.seal(output) { stream ->
                stream.write(bytes(SEGMENT_SIZE + 10))
                throw IOException("placeholder failure")
            }
            fail("The failure was swallowed")
        } catch (_: IOException) {
        }

        assertDoesNotOpen(output.toByteArray())
    }

    @Test
    fun aPlainFileIsRejected() {
        assertDoesNotOpen(bytes(SEGMENT_SIZE))
    }

    @Test
    fun namesAreStablePerKeyAndHideTheHash() {
        val hash = "0".repeat(64)
        val other = FileCipher.create(ByteArray(32).also(random::nextBytes), random)

        assertEquals(cipher.sealedName(hash), cipher.sealedName(hash))
        assertNotEquals(cipher.sealedName(hash), other.sealedName(hash))
        assertNotEquals(hash, cipher.sealedName(hash))
        assertEquals(64, cipher.sealedName(hash).length)
    }

    @Test
    fun ephemeralCiphersDoNotShareKeys() {
        val sealed = FileCipher.ephemeral(random).let { first ->
            ByteArrayOutputStream().also { output -> first.seal(output) { it.write(bytes(10)) } }.toByteArray()
        }

        try {
            FileCipher.ephemeral(random).open(ByteArrayInputStream(sealed), sealed.size.toLong()).use { it.readBytes() }
            fail("Opened with another ephemeral key")
        } catch (_: SealedFileException) {
        }
    }

    private fun bytes(size: Int) = ByteArray(size).also(random::nextBytes)

    private fun seal(plain: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        // Odd write sizes exercise the segment buffering
        cipher.seal(output) { stream -> plain.asList().chunked(7_001).forEach { stream.write(it.toByteArray()) } }
        return output.toByteArray()
    }

    private fun open(sealed: ByteArray): ByteArray =
        cipher.open(ByteArrayInputStream(sealed), sealed.size.toLong()).use { it.readBytes() }

    private fun assertDoesNotOpen(sealed: ByteArray) {
        try {
            open(sealed)
            fail("A damaged sealed file opened")
        } catch (_: SealedFileException) {
        }
    }
}
