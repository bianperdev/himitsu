// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.key

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Reads and writes the master key file atomically.
 *
 * - A write goes to a temporary file that is synced to disk and then renamed over the old
 *   file, so a crash never leaves a half-written key file.
 * - The file lives in `noBackupFilesDir`: it is useless on another device because the Keystore
 *   key that seals it never leaves this one.
 */
class MasterKeyFileStore(private val mFile: File) {

    /** Whether a master key file exists. */
    fun exists(): Boolean = mFile.isFile

    /**
     * Reads and parses the master key file.
     *
     * @throws IOException when the file cannot be read
     * @throws MasterKeyFileFormatException when the content is damaged
     */
    fun read(): MasterKeyFile = MasterKeyFileCodec.decode(mFile.readBytes())

    /**
     * Replaces the master key file with [file].
     *
     * @throws IOException when the file cannot be written; the previous file is then left intact
     */
    fun write(file: MasterKeyFile) {
        mFile.parentFile?.mkdirs()
        val temp = File(mFile.parentFile, mFile.name + ".tmp")
        FileOutputStream(temp).use { out ->
            out.write(MasterKeyFileCodec.encode(file))
            out.fd.sync()
        }
        Files.move(
            temp.toPath(),
            mFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    }

    /**
     * Renames the key file with [suffix] instead of deleting it, so a user who starts over can
     * still hand the old files to a recovery attempt later.
     */
    fun moveAside(suffix: String) {
        if (!exists()) return
        Files.move(mFile.toPath(), File(mFile.parentFile, mFile.name + suffix).toPath())
    }
}
