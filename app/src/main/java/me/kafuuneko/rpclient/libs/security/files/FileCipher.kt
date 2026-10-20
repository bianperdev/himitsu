// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.files

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import me.kafuuneko.rpclient.libs.security.key.Hkdf

/** A sealed file is malformed, truncated, was modified, or was sealed with another key. */
class SealedFileException(message: String) : IOException(message)

/**
 * Seals files with AES-256-GCM in 64 KiB segments, so images are encrypted and read as streams
 * without holding a whole file in memory.
 *
 * Format, version 1:
 * - header: magic `HMF1`, a version byte and a random 32-byte salt
 * - segments: up to [SEGMENT_SIZE] plaintext bytes each, followed by a 16-byte tag. Every
 *   segment is full except the last one, which is empty only when it is the only one.
 *
 * Each file gets its own key, derived from the root key and the salt. A segment's nonce holds
 * its index and a "last segment" flag, and the header is authenticated with every segment, so
 * reordering, truncating or extending a file is detected. Nothing is returned to the reader
 * before the tag of its segment has been verified.
 *
 * Instances are safe to share between threads.
 */
class FileCipher private constructor(private val mRootKey: ByteArray, private val mRandom: SecureRandom) {
    private val mNameKey = Hkdf.deriveSha256(mRootKey, ByteArray(0), NAME_INFO, KEY_SIZE)

    /**
     * File name for content with SHA-256 [contentHash], keyed so that the name does not reveal
     * which image it holds.
     */
    fun sealedName(contentHash: String): String {
        val mac = Mac.getInstance(MAC_ALGORITHM)
        mac.init(SecretKeySpec(mNameKey, MAC_ALGORITHM))
        return mac.doFinal(contentHash.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /**
     * Writes a sealed file to [output] with the plaintext that [write] produces.
     *
     * The final segment is written only when [write] returns normally; after an exception the
     * output holds an incomplete file that never opens. [output] is flushed but not closed, and
     * closing the stream given to [write] has no effect.
     */
    fun <T> seal(output: OutputStream, write: (OutputStream) -> T): T {
        val sealer = Sealer(output)
        try {
            val result = write(sealer)
            sealer.finish()
            output.flush()
            return result
        } finally {
            sealer.wipe()
        }
    }

    /**
     * Writes [target] as a sealed file, replacing it, and syncs it to disk. On failure
     * [target] is deleted.
     */
    suspend fun <T> sealFile(target: File, write: suspend (OutputStream) -> T): T {
        var success = false
        try {
            val result = FileOutputStream(target).use { output ->
                val sealer = Sealer(output)
                try {
                    write(sealer).also {
                        sealer.finish()
                        output.flush()
                        output.fd.sync()
                    }
                } finally {
                    sealer.wipe()
                }
            }
            success = true
            return result
        } finally {
            if (!success) target.delete()
        }
    }

    /**
     * Opens a sealed file of [sealedLength] bytes read from [input]. The returned stream owns
     * [input], which is closed right away when the header is invalid, and throws
     * [SealedFileException] when the content does not verify.
     */
    fun open(input: InputStream, sealedLength: Long): InputStream = try {
        OpeningStream(input, sealedLength)
    } catch (e: Throwable) {
        input.close()
        throw e
    }

    /** Opens the sealed file [source]. */
    fun open(source: File): InputStream = open(source.inputStream(), source.length())

    /** Wipes the keys. The instance must not be used afterwards. */
    fun destroy() {
        mRootKey.fill(0)
        mNameKey.fill(0)
    }

    override fun toString(): String = "FileCipher([REDACTED])"

    private fun segmentKey(salt: ByteArray): SecretKeySpec {
        val key = Hkdf.deriveSha256(mRootKey, salt, SEGMENT_INFO, KEY_SIZE)
        try {
            return SecretKeySpec(key, "AES")
        } finally {
            key.fill(0)
        }
    }

    /** Plaintext side of a sealed file being written. Only its owner finishes and wipes it. */
    private inner class Sealer(private val mOut: OutputStream) : OutputStream() {
        private val mHeader = header(ByteArray(SALT_SIZE).also(mRandom::nextBytes))
        private val mKey = segmentKey(mHeader.copyOfRange(MAGIC.size + 1, HEADER_SIZE))
        private val mCipher = Cipher.getInstance(TRANSFORMATION)
        private val mBuffer = ByteArray(SEGMENT_SIZE)
        private val mOutputBuffer = ByteArray(SEGMENT_SIZE + TAG_SIZE)
        private var mFill = 0
        private var mIndex = 0
        private var mFinished = false

        init {
            mOut.write(mHeader)
        }

        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()), 0, 1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (off < 0 || len < 0 || off > b.size - len) throw IndexOutOfBoundsException()
            check(!mFinished) { "Sealed file already finished" }
            var offset = off
            var remaining = len
            while (remaining > 0) {
                // A full segment is written only once more data follows, so the last one is known
                if (mFill == SEGMENT_SIZE) writeSegment(last = false)
                val count = minOf(remaining, SEGMENT_SIZE - mFill)
                System.arraycopy(b, offset, mBuffer, mFill, count)
                mFill += count
                offset += count
                remaining -= count
            }
        }

        /** Only the owner ends the file, by writing its last segment. */
        override fun close() = Unit

        fun finish() {
            check(!mFinished) { "Sealed file already finished" }
            writeSegment(last = true)
            mFinished = true
        }

        fun wipe() {
            mBuffer.fill(0)
            mOutputBuffer.fill(0)
        }

        private fun writeSegment(last: Boolean) {
            mCipher.init(Cipher.ENCRYPT_MODE, mKey, GCMParameterSpec(TAG_SIZE * 8, nonce(mIndex, last)))
            mCipher.updateAAD(mHeader)
            val size = mCipher.doFinal(mBuffer, 0, mFill, mOutputBuffer, 0)
            mOut.write(mOutputBuffer, 0, size)
            mIndex = Math.addExact(mIndex, 1)
            mFill = 0
        }
    }

    private inner class OpeningStream(private val mIn: InputStream, sealedLength: Long) : InputStream() {
        private val mHeader = ByteArray(HEADER_SIZE)
        private val mSegmentCount: Long
        private val mLastSegmentSize: Int
        private val mKey: SecretKeySpec
        private val mCipher = Cipher.getInstance(TRANSFORMATION)
        private val mSealed = ByteArray(SEGMENT_SIZE + TAG_SIZE)
        private val mPlain = ByteArray(SEGMENT_SIZE + TAG_SIZE)
        private var mIndex = 0L
        private var mPosition = 0
        private var mLimit = 0

        init {
            plainLength(sealedLength)
            val body = sealedLength - HEADER_SIZE
            mSegmentCount = (body + SEALED_SEGMENT_SIZE - 1) / SEALED_SEGMENT_SIZE
            mLastSegmentSize = (body - (mSegmentCount - 1) * SEALED_SEGMENT_SIZE).toInt()
            if (mSegmentCount > Int.MAX_VALUE) throw SealedFileException("Sealed file too long")
            readFully(mHeader, HEADER_SIZE)
            if (!mHeader.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) || mHeader[MAGIC.size] != VERSION) {
                throw SealedFileException("Not a sealed file")
            }
            mKey = segmentKey(mHeader.copyOfRange(MAGIC.size + 1, HEADER_SIZE))
        }

        override fun read(): Int {
            if (!fill()) return -1
            return mPlain[mPosition++].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (off < 0 || len < 0 || off > b.size - len) throw IndexOutOfBoundsException()
            if (len == 0) return 0
            if (!fill()) return -1
            val count = minOf(len, mLimit - mPosition)
            System.arraycopy(mPlain, mPosition, b, off, count)
            mPosition += count
            return count
        }

        override fun available(): Int = mLimit - mPosition

        override fun close() {
            mPlain.fill(0)
            mPosition = 0
            mLimit = 0
            mIn.close()
        }

        /** Decrypts the next segment when the current one is used up. */
        private fun fill(): Boolean {
            while (mPosition == mLimit) {
                if (mIndex == mSegmentCount) return false
                val last = mIndex == mSegmentCount - 1
                val size = if (last) mLastSegmentSize else SEALED_SEGMENT_SIZE.toInt()
                readFully(mSealed, size)
                try {
                    mCipher.init(Cipher.DECRYPT_MODE, mKey, GCMParameterSpec(TAG_SIZE * 8, nonce(mIndex.toInt(), last)))
                    mCipher.updateAAD(mHeader)
                    mLimit = mCipher.doFinal(mSealed, 0, size, mPlain, 0)
                } catch (_: GeneralSecurityException) {
                    mLimit = 0
                    throw SealedFileException("Sealed file does not verify")
                }
                mPosition = 0
                mIndex++
            }
            return true
        }

        private fun readFully(buffer: ByteArray, size: Int) {
            var read = 0
            while (read < size) {
                val count = mIn.read(buffer, read, size - read)
                if (count < 0) throw SealedFileException("Sealed file truncated")
                read += count
            }
        }
    }

    companion object {
        /** Plaintext bytes per segment. Part of the on-disk format. */
        const val SEGMENT_SIZE = 64 * 1024
        const val TAG_SIZE = 16
        private const val KEY_SIZE = 32
        private const val SALT_SIZE = 32
        private const val NONCE_SIZE = 12
        private val MAGIC = byteArrayOf('H'.code.toByte(), 'M'.code.toByte(), 'F'.code.toByte(), '1'.code.toByte())
        private const val VERSION: Byte = 1
        const val HEADER_SIZE = 4 + 1 + SALT_SIZE
        private const val SEALED_SEGMENT_SIZE = (SEGMENT_SIZE + TAG_SIZE).toLong()
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val MAC_ALGORITHM = "HmacSHA256"

        // Part of the on-disk format: changing one makes every sealed file unreadable
        private val SEGMENT_INFO = "himitsu/v1/files/segment".toByteArray(Charsets.UTF_8)
        private val NAME_INFO = "himitsu/v1/files/name".toByteArray(Charsets.UTF_8)

        /** Size of the sealed file that holds [plainLength] bytes. */
        fun sealedLength(plainLength: Long): Long {
            require(plainLength >= 0) { "Negative length" }
            val segments = maxOf(1, (plainLength + SEGMENT_SIZE - 1) / SEGMENT_SIZE)
            return HEADER_SIZE + plainLength + segments * TAG_SIZE
        }

        /**
         * Plaintext size of a sealed file of [sealedLength] bytes, computed without reading it.
         *
         * @throws SealedFileException when no sealed file can have that length
         */
        fun plainLength(sealedLength: Long): Long {
            val body = sealedLength - HEADER_SIZE
            if (body < TAG_SIZE) throw SealedFileException("Sealed file too short")
            val segments = (body + SEALED_SEGMENT_SIZE - 1) / SEALED_SEGMENT_SIZE
            val last = body - (segments - 1) * SEALED_SEGMENT_SIZE
            // Only a lone last segment may be empty
            if (last < TAG_SIZE || (segments > 1 && last == TAG_SIZE.toLong())) {
                throw SealedFileException("Invalid sealed file length")
            }
            return body - segments * TAG_SIZE
        }

        /**
         * Creates a cipher over a 256-bit root key, taking ownership of [rootKey].
         */
        fun create(rootKey: ByteArray, random: SecureRandom = SecureRandom()): FileCipher {
            require(rootKey.size == KEY_SIZE) { "256-bit key expected" }
            return FileCipher(rootKey, random)
        }

        /**
         * Creates a cipher with a random key that lives only in this process, for temporary
         * files: if the process dies, what it sealed can never be opened again.
         */
        fun ephemeral(random: SecureRandom = SecureRandom()): FileCipher =
            FileCipher(ByteArray(KEY_SIZE).also(random::nextBytes), random)

        private fun header(salt: ByteArray): ByteArray = MAGIC + VERSION + salt

        private fun nonce(index: Int, last: Boolean): ByteArray = ByteArray(NONCE_SIZE).also { nonce ->
            nonce[7] = (index ushr 24).toByte()
            nonce[8] = (index ushr 16).toByte()
            nonce[9] = (index ushr 8).toByte()
            nonce[10] = index.toByte()
            nonce[11] = if (last) 1 else 0
        }
    }
}
