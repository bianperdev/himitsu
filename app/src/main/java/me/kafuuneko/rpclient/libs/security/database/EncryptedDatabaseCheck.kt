// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.database

import android.database.sqlite.SQLiteDatabaseCorruptException
import java.io.File
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException

/** Whether Room will be able to open an encrypted database file. */
enum class DatabaseCheck {
    /** The file opens with its key, does not exist yet, or failed for a reason that may pass. */
    Usable,

    /** The file does not open with its key: it is damaged, or encrypted with another key. */
    Damaged
}

/**
 * Opens an encrypted database read only before Room does, to find a damaged file in time.
 *
 * - Runs before the database keys are released to Room, so a damaged file stops the startup on
 *   the error screen instead of failing the first query of every screen.
 * - Only errors that say the file itself cannot be read count as damage. Anything else (a busy
 *   file, a full disk) is left to Room and the usual error handling, so a healthy database is
 *   never reported as damaged.
 * - Never deletes anything: the error handler ignores corruption reports instead of removing
 *   the file, as Android's default handler would without SQLCipher.
 */
object EncryptedDatabaseCheck {
    private val KEEP_FILES = DatabaseErrorHandler { _, _ -> }

    /**
     * Checks [file] with the raw-key [passphrase] of its database.
     *
     * @param passphrase the SQLCipher raw-key passphrase; not wiped here, the caller owns it
     * @return [DatabaseCheck.Damaged] only when the file is certainly unreadable with this key
     */
    fun check(file: File, passphrase: ByteArray): DatabaseCheck {
        if (DatabaseFiles.kindOf(file) != DatabaseFileKind.Encrypted) return DatabaseCheck.Usable
        return try {
            SQLiteDatabase.openDatabase(file.path, passphrase, null, SQLiteDatabase.OPEN_READONLY, KEEP_FILES, null).use { database ->
                database.rawQuery("SELECT count(*) FROM sqlite_master", emptyArray<String>()).use { it.moveToFirst() }
            }
            DatabaseCheck.Usable
        } catch (_: SQLiteNotADatabaseException) {
            DatabaseCheck.Damaged
        } catch (_: SQLiteDatabaseCorruptException) {
            DatabaseCheck.Damaged
        } catch (_: Exception) {
            DatabaseCheck.Usable
        }
    }
}
