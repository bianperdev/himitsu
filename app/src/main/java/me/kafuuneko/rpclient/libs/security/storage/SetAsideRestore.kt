// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.storage

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.room.repository.FILE_REPOSITORY_DIR_NAME
import me.kafuuneko.rpclient.libs.security.database.DatabaseFiles
import me.kafuuneko.rpclient.libs.security.database.EncryptedDatabase
import me.kafuuneko.rpclient.libs.security.database.SqlCipherKey
import me.kafuuneko.rpclient.libs.security.key.MasterKeyFileStore
import me.kafuuneko.rpclient.libs.security.key.MasterKeyVault
import me.kafuuneko.rpclient.libs.security.key.RecoveryCode
import me.kafuuneko.rpclient.libs.security.key.SetAsideUnlock
import net.zetetic.database.sqlcipher.SQLiteDatabase

/** Result of preparing to restore a set-aside copy. */
sealed class RestorePreparation {
    /** The swap is recorded; it happens when the app starts again. */
    data object Scheduled : RestorePreparation()

    /** The copy's own Keystore key is gone; its recovery code is needed. */
    data object NeedsCode : RestorePreparation()
    data object WrongCode : RestorePreparation()

    /** The copy never had a recovery code and its Keystore key is gone. */
    data object NotConfigured : RestorePreparation()

    /** The copy cannot be restored; nothing was changed. */
    data object Failed : RestorePreparation()
}

/**
 * Puts a set-aside copy back in place of the data in use.
 *
 * Nothing is deleted: the data in use is itself set aside as [SetAsideKind.Replaced], and can
 * be restored the same way, with its own Keystore key and no code. The swap only renames files,
 * at the very start of the next process, before anything opens them ([RestoreJournal]).
 */
class SetAsideRestorer(
    private val mContext: Context,
    private val mVault: MasterKeyVault,
    private val mFiles: ProtectedFiles
) {
    /** Whether [set] can be restored: it is encrypted, kept its key file and holds the main database. */
    fun canRestore(set: SetAside): Boolean =
        set.kind != SetAsideKind.Unusable && set.hasKeyFile && primaryDatabaseOf(set.suffix).isFile

    /**
     * Opens the copy's key, checks that it opens the copy's main database, stores the key sealed
     * under a new Keystore key, and records the swap for the next start.
     *
     * @param code the recovery code of the copy, or null to try its own Keystore key first
     */
    suspend fun prepare(suffix: String, code: RecoveryCode?): RestorePreparation = withContext(Dispatchers.IO) {
        val set = mFiles.setAside().firstOrNull { it.suffix == suffix }
        if (set == null || !canRestore(set)) return@withContext RestorePreparation.Failed
        val restoredKey = File(mContext.noBackupFilesDir, MASTER_KEY_FILE_NAME + RESTORED_SUFFIX)
        val source = MasterKeyFileStore(File(mContext.noBackupFilesDir, MASTER_KEY_FILE_NAME + suffix))
        val masterKey = when (val result = mVault.unlockSetAside(source, MasterKeyFileStore(restoredKey), code)) {
            is SetAsideUnlock.Unlocked -> result.masterKey
            SetAsideUnlock.NeedsCode -> return@withContext RestorePreparation.NeedsCode
            SetAsideUnlock.WrongCode -> return@withContext RestorePreparation.WrongCode
            SetAsideUnlock.NotConfigured -> return@withContext RestorePreparation.NotConfigured
            is SetAsideUnlock.Failed -> return@withContext RestorePreparation.Failed
        }
        try {
            val key = masterKey.derive(EncryptedDatabase.Primary.purpose)
            val opens = try {
                opensWith(primaryDatabaseOf(suffix), key)
            } finally {
                key.fill(0)
            }
            // The re-sealed key is only our own copy; without a working key it is useless
            if (!opens || !RestoreJournal.schedule(mContext, suffix, ProtectedFiles.newSuffix(SetAsideKind.Replaced))) {
                restoredKey.delete()
                return@withContext RestorePreparation.Failed
            }
            RestorePreparation.Scheduled
        } finally {
            masterKey.destroy()
        }
    }

    private fun primaryDatabaseOf(suffix: String): File =
        File(mContext.getDatabasePath(EncryptedDatabase.Primary.fileName).path + suffix)

    /** Whether [key] opens [database], read only. */
    private fun opensWith(database: File, key: ByteArray): Boolean {
        val passphrase = SqlCipherKey.rawKeyPassphrase(key)
        return try {
            SQLiteDatabase.openDatabase(database.path, passphrase, null, SQLiteDatabase.OPEN_READONLY, null, null).use { db ->
                db.rawQuery("SELECT count(*) FROM sqlite_master", emptyArray<String>()).use { it.moveToFirst() }
            }
        } catch (_: Exception) {
            false
        } finally {
            passphrase.fill(0)
        }
    }

    companion object {
        /** The copy's key file sealed under a new Keystore key, waiting for the swap. */
        internal const val RESTORED_SUFFIX = ".restored"
    }
}

/**
 * Journal of a restore, so that the swap survives a crash at any point.
 *
 * Phase 1 moves the data in use aside under the "replaced" suffix; phase 2 moves the copy into
 * place. Each step renames only what is still at its source, so a step can run twice. The
 * journal is deleted only after phase 2. Holds suffixes only, never content.
 */
object RestoreJournal {
    private const val FILE_NAME = "himitsu_restore.journal"
    private const val SOURCE = "source"
    private const val REPLACED = "replaced"
    private const val PHASE = "phase"

    /** Records a restore of [source]; the data in use will be set aside as [replaced]. */
    fun schedule(context: Context, source: String, replaced: String): Boolean =
        runCatching { write(context, source, replaced, phase = 1) }.isSuccess

    /**
     * Finishes a recorded restore. Must run at the start of the process, before preferences,
     * databases or stored files are opened.
     *
     * @return false when a step failed; the journal is kept and the next start continues
     */
    fun completePending(context: Context): Boolean {
        val journal = File(context.noBackupFilesDir, FILE_NAME)
        if (!journal.isFile) return true
        return try {
            val values = Properties().apply { journal.inputStream().use { load(it) } }
            val source = values.getProperty(SOURCE) ?: return false
            val replaced = values.getProperty(REPLACED) ?: return false
            if (values.getProperty(PHASE) == "1") {
                swap(context, aside = replaced, toAside = true, source = source)
                write(context, source, replaced, phase = 2)
            }
            swap(context, aside = source, toAside = false, source = source)
            journal.delete()
            true
        } catch (_: IOException) {
            false
        }
    }

    /**
     * Moves every protected item between its live name and its name with [aside].
     *
     * @param toAside true for phase 1 (live to aside), false for phase 2 (aside to live)
     * @param source suffix of the copy being restored, which tells which preference files it has
     * @throws IOException when an item cannot be moved, or its destination is already taken
     */
    private fun swap(context: Context, aside: String, toAside: Boolean, source: String) {
        fun pair(live: File): Pair<File, File> = File(live.path + aside).let { if (toAside) live to it else it to live }
        EncryptedDatabase.entries.forEach { database ->
            val file = context.getDatabasePath(database.fileName)
            listOf(file, File(file.path + ".plaintext")).forEach { live ->
                val (from, to) = pair(live)
                if (from.exists()) {
                    if (to.exists()) throw IOException("Destination taken")
                    DatabaseFiles.move(from, to)
                }
            }
        }
        val keyFile = File(context.noBackupFilesDir, MASTER_KEY_FILE_NAME)
        if (toAside) move(keyFile, File(keyFile.path + aside)) else move(File(keyFile.path + SetAsideRestorer.RESTORED_SUFFIX), keyFile)
        val repository = File(context.applicationInfo.dataDir, "app_$FILE_REPOSITORY_DIR_NAME")
        val (from, to) = pair(repository)
        // A directory without files (left by the app or by "start over") holds nothing to keep
        if (from.exists() && to.exists() && to.walk().none { it.isFile }) to.deleteRecursively()
        move(from, to)
        // Only the preference files the copy has are swapped; the others (the lock switches) stay
        val preferences = File(context.applicationInfo.dataDir, "shared_prefs")
        preferences.listFiles().orEmpty().filter { it.name.endsWith(".xml$source") }.forEach { copy ->
            val live = File(preferences, copy.name.removeSuffix(source))
            if (toAside) move(live, File(live.path + aside)) else move(copy, live)
        }
    }

    /** Renames [from] to [to] when [from] exists; never replaces anything. */
    private fun move(from: File, to: File) {
        if (!from.exists()) return
        if (to.exists()) throw IOException("Destination taken")
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    private fun write(context: Context, source: String, replaced: String, phase: Int) {
        val journal = File(context.noBackupFilesDir, FILE_NAME)
        val temp = File(journal.path + ".tmp")
        FileOutputStream(temp).use { output ->
            Properties().apply {
                setProperty(SOURCE, source)
                setProperty(REPLACED, replaced)
                setProperty(PHASE, phase.toString())
            }.store(output, null)
            output.fd.sync()
        }
        Files.move(temp.toPath(), journal.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
