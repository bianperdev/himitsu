// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.database

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** What a database file on disk contains, judged from its header. */
enum class DatabaseFileKind {
    Missing,

    /** Zero bytes: SQLite created the file but never wrote a page. Holds no data. */
    Empty,

    /** Starts with the plain SQLite header: readable without any key. */
    Plaintext,

    /** Anything else: a SQLCipher file starts with a random salt instead of the header. */
    Encrypted
}

/**
 * File helpers for SQLite databases and their companion files (`-wal`, `-shm`, `-journal`).
 *
 * A database is always moved together with its companions: a WAL file left behind would be
 * replayed into the wrong database.
 */
internal object DatabaseFiles {
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private val COMPANION_SUFFIXES = listOf("-wal", "-shm", "-journal")

    /**
     * Classifies [file] by its first 16 bytes.
     *
     * A file that exists but cannot be read counts as encrypted: the safe answer, because it
     * prevents a new key from being created over data that may be there.
     */
    fun kindOf(file: File): DatabaseFileKind {
        if (!file.isFile) return DatabaseFileKind.Missing
        if (file.length() == 0L) return DatabaseFileKind.Empty
        if (file.length() < SQLITE_HEADER.size) return DatabaseFileKind.Encrypted
        val header = ByteArray(SQLITE_HEADER.size)
        try {
            RandomAccessFile(file, "r").use { it.readFully(header) }
        } catch (_: IOException) {
            return DatabaseFileKind.Encrypted
        }
        return if (header.contentEquals(SQLITE_HEADER)) DatabaseFileKind.Plaintext else DatabaseFileKind.Encrypted
    }

    /** The database file followed by its companion files that exist. */
    fun withCompanions(file: File): List<File> =
        listOf(file) + COMPANION_SUFFIXES.map { File(file.path + it) }.filter { it.exists() }

    /**
     * Renames [file] and its companions so that their names start with [target]'s name.
     *
     * @throws IOException when a file cannot be moved
     */
    fun move(file: File, target: File) {
        withCompanions(file).forEach { source ->
            val suffix = source.name.removePrefix(file.name)
            Files.move(source.toPath(), File(target.path + suffix).toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Deletes [file] and its companions. Only used for copies that were verified elsewhere. */
    fun delete(file: File) {
        withCompanions(file).forEach { it.delete() }
    }
}
