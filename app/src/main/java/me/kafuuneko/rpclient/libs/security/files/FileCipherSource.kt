// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.files

import android.content.Context
import java.io.File
import me.kafuuneko.rpclient.libs.room.repository.FILE_REPOSITORY_DIR_NAME

/** Gives the cipher of stored files once the encrypted storage is open. */
fun interface FileCipherSource {
    /** Blocks until the master key is released, like the database keys. */
    fun fileCipher(): FileCipher
}

/**
 * Where the file repository keeps its files.
 *
 * Files from builds before P-06 sit in plain form directly in the repository directory, named
 * by their SHA-256. Sealed files live in [SEALED_DIRECTORY], so the location alone tells the
 * format and a plain file can never be taken for a sealed one or the other way round.
 */
object StoredFileLayout {
    /** Name passed to [Context.getDir], which adds the `app_` prefix. */
    const val REPOSITORY_DIRECTORY = FILE_REPOSITORY_DIR_NAME
    const val SEALED_DIRECTORY = "sealed"

    fun repositoryDirectory(context: Context): File = context.getDir(REPOSITORY_DIRECTORY, Context.MODE_PRIVATE)

    fun sealedDirectory(context: Context): File = File(repositoryDirectory(context), SEALED_DIRECTORY)

    /** Whether any sealed file exists, so that a missing key file must not be replaced. */
    fun hasSealedFiles(context: Context): Boolean = sealedDirectory(context).walk().any { it.isFile }

}
