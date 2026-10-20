// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.room.repository

import androidx.annotation.VisibleForTesting
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import me.kafuuneko.rpclient.libs.security.files.FileCipher

/**
 * Himitsu (P-06): a file of [FileRepository], read through its cipher.
 *
 * Valid only inside the block that received it, which holds the repository's protection
 * against deletion. Files not yet moved to the sealed format are read as they are.
 */
class StoredFile internal constructor(private val mFile: File, private val mCipher: FileCipher?) {
    /**
     * Size of the content in bytes.
     *
     * @throws java.io.IOException when a sealed file has an impossible length
     */
    val length: Long
        get() = if (mCipher == null) mFile.length() else FileCipher.plainLength(mFile.length())

    /**
     * Opens the content. Reading throws [java.io.IOException] when a sealed file does not
     * verify, so damaged bytes are never returned.
     */
    fun openStream(): InputStream = mCipher?.open(mFile) ?: mFile.inputStream()

    fun readBytes(): ByteArray = openStream().use { it.readBytes() }

    /** Lets tests simulate a file lost outside the app. */
    @VisibleForTesting
    internal fun deleteForTest(): Boolean = mFile.delete()

    override fun toString(): String = "StoredFile([REDACTED])"
}

/** Destination of a generated file in [FileRepository.prepareGenerated]. */
class StoredFileTarget internal constructor(private val mFile: File, private val mCipher: FileCipher) {
    /**
     * Replaces the content with what [write] produces. On failure nothing usable is left.
     * Closing the stream given to [write] has no effect.
     */
    suspend fun <T> write(write: suspend (OutputStream) -> T): T = mCipher.sealFile(mFile, write)

    /** The content written so far. */
    fun content(): StoredFile = StoredFile(mFile, mCipher)

    internal val exists: Boolean get() = mFile.isFile

    override fun toString(): String = "StoredFileTarget([REDACTED])"
}
