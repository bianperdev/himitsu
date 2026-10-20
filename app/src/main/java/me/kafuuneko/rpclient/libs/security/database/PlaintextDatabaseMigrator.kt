// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.database

import android.database.Cursor
import android.database.sqlite.SQLiteDatabaseCorruptException
import java.io.File
import java.io.IOException
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException

/**
 * A plain database could not be migrated. The plain original is left untouched.
 *
 * @property permanent true when retrying cannot help: the plain file is damaged, its copy does
 *   not match it, or two plain copies exist and neither can be chosen safely. False for errors
 *   that may pass, such as a full disk.
 */
class DatabaseMigrationException(
    message: String,
    cause: Throwable? = null,
    val permanent: Boolean = false
) : IOException(message, cause)

/**
 * Moves a plain SQLite database from an earlier Himitsu build into SQLCipher.
 *
 * Sequence, designed so that a crash at any point loses nothing:
 * - export the plain database into `<name>.encrypting` with `sqlcipher_export`
 * - verify the copy: same `user_version` and same row count in every table
 * - rename the plain original (with its WAL files) to `<name>.plaintext`
 * - rename the encrypted copy to `<name>`
 * - delete `<name>.plaintext`
 *
 * On the next start an interrupted run is finished or rolled back from whatever files exist.
 * Deleting a file on flash storage does not guarantee the old blocks are erased; that limit
 * is documented for users.
 */
class PlaintextDatabaseMigrator {

    /**
     * Encrypts [databaseFile] with [passphrase] if it is still plain.
     *
     * @return true when a plain database was migrated during this call
     * @throws DatabaseMigrationException when the migration fails; the plain data is kept
     */
    fun migrateIfNeeded(databaseFile: File, passphrase: ByteArray): Boolean {
        val backup = File(databaseFile.path + PLAINTEXT_SUFFIX)
        val temp = File(databaseFile.path + ENCRYPTING_SUFFIX)
        // Finish or roll back a run interrupted between the two renames
        if (DatabaseFiles.anyExists(backup)) {
            when (DatabaseFiles.kindOf(databaseFile)) {
                DatabaseFileKind.Encrypted -> DatabaseFiles.delete(backup)
                DatabaseFileKind.Missing, DatabaseFileKind.Empty -> DatabaseFiles.move(backup, databaseFile)
                DatabaseFileKind.Plaintext -> throw DatabaseMigrationException("Two plain copies of one database", permanent = true)
            }
        }
        if (DatabaseFiles.kindOf(databaseFile) != DatabaseFileKind.Plaintext) return false
        // A leftover encrypted copy is incomplete; the plain original is still the reference
        DatabaseFiles.delete(temp)
        try {
            export(databaseFile, temp, passphrase)
            verify(databaseFile, temp, passphrase)
        } catch (e: Exception) {
            DatabaseFiles.delete(temp)
            throw e as? DatabaseMigrationException ?: DatabaseMigrationException("Export failed", e, permanent = isDamaged(e))
        }
        // Swap the files, then remove the plain copy
        try {
            DatabaseFiles.move(databaseFile, backup)
            DatabaseFiles.move(temp, databaseFile)
        } catch (e: IOException) {
            throw DatabaseMigrationException("Swap failed", e)
        }
        DatabaseFiles.delete(backup)
        return true
    }

    private fun export(plain: File, target: File, passphrase: ByteArray) {
        // ATTACH inherits the open flags of the main database, so CREATE is needed for the copy
        val database = openPlain(plain, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY)
        try {
            // Fold any WAL content into the main file so the export sees every committed row
            database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", emptyArray<String>()).use(Cursor::moveToFirst)
            // Key and path are bound as arguments, so they never appear in SQL text
            database.execSQL("ATTACH DATABASE ? AS $EXPORT_SCHEMA KEY ?", arrayOf<Any>(target.path, passphrase))
            database.rawQuery("SELECT sqlcipher_export('$EXPORT_SCHEMA')", emptyArray<String>()).use(Cursor::moveToFirst)
            database.execSQL("PRAGMA $EXPORT_SCHEMA.user_version = ${database.version}")
            database.execSQL("DETACH DATABASE $EXPORT_SCHEMA")
        } finally {
            database.close()
        }
    }

    private fun verify(plain: File, encrypted: File, passphrase: ByteArray) {
        val source = openPlain(plain, SQLiteDatabase.OPEN_READONLY)
        try {
            val copy = SQLiteDatabase.openDatabase(encrypted.path, passphrase, null, SQLiteDatabase.OPEN_READONLY, null, null)
            try {
                if (source.version != copy.version) throw DatabaseMigrationException("Schema version mismatch", permanent = true)
                val tables = tableNames(source)
                if (tables != tableNames(copy)) throw DatabaseMigrationException("Table list mismatch", permanent = true)
                tables.forEach { table ->
                    if (rowCount(source, table) != rowCount(copy, table)) {
                        throw DatabaseMigrationException("Row count mismatch", permanent = true)
                    }
                }
            } finally {
                copy.close()
            }
        } finally {
            source.close()
        }
    }

    /** Whether [error] says the plain file itself is damaged, which no retry can fix. */
    private fun isDamaged(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { it is SQLiteDatabaseCorruptException || it is SQLiteNotADatabaseException }

    private fun openPlain(file: File, flags: Int): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.path, "", null, flags, null)

    private fun tableNames(database: SQLiteDatabase): List<String> =
        database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            emptyArray<String>()
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun rowCount(database: SQLiteDatabase, table: String): Long {
        val quoted = "\"" + table.replace("\"", "\"\"") + "\""
        return database.rawQuery("SELECT COUNT(*) FROM $quoted", emptyArray<String>())
            .use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else -1L }
    }

    private companion object {
        const val PLAINTEXT_SUFFIX = ".plaintext"
        const val ENCRYPTING_SUFFIX = ".encrypting"
        const val EXPORT_SCHEMA = "himitsu_encrypted"
    }
}
