// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.room.repository

import java.io.File
import me.kafuuneko.rpclient.libs.security.files.FileCipher
import me.kafuuneko.rpclient.libs.security.files.FileCipherSource

/**
 * Himitsu (P-06): one in-memory cipher shared by every repository the instrumented tests
 * create, so two instances over the same directory read each other's files.
 */
object TestFileCipher : FileCipherSource {
    val cipher: FileCipher = FileCipher.ephemeral()

    override fun fileCipher(): FileCipher = cipher

    /** The sealed copy of [hash] in a repository under [directory]. */
    fun sealedFile(directory: File, hash: String): File =
        File(directory, "repository/sealed/${cipher.sealedName(hash)}")

    /** Drafts of a repository under [directory]. */
    fun stagingDirectory(directory: File): File = File(directory, "repository/sealed/staging")
}
