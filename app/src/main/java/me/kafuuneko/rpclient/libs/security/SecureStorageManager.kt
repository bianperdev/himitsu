// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import android.content.Context
import android.database.sqlite.SQLiteException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.security.database.DatabaseCheck
import me.kafuuneko.rpclient.libs.security.database.DatabaseFileKind
import me.kafuuneko.rpclient.libs.security.database.DatabaseFiles
import me.kafuuneko.rpclient.libs.security.database.DatabaseKeySource
import me.kafuuneko.rpclient.libs.security.database.DatabaseMigrationException
import me.kafuuneko.rpclient.libs.security.database.EncryptedDatabase
import me.kafuuneko.rpclient.libs.security.database.EncryptedDatabaseCheck
import me.kafuuneko.rpclient.libs.security.database.KeyedOpenHelperFactory
import me.kafuuneko.rpclient.libs.security.database.PlaintextDatabaseMigrator
import me.kafuuneko.rpclient.libs.security.database.SqlCipherKey
import me.kafuuneko.rpclient.libs.security.database.SqlCipherLog
import me.kafuuneko.rpclient.libs.security.files.FileCipher
import me.kafuuneko.rpclient.libs.security.files.FileCipherSource
import me.kafuuneko.rpclient.libs.security.files.StoredFileLayout
import me.kafuuneko.rpclient.libs.security.key.KeyPurpose
import me.kafuuneko.rpclient.libs.security.key.MasterKey
import me.kafuuneko.rpclient.libs.security.key.MasterKeyVault
import me.kafuuneko.rpclient.libs.security.key.RecoveryCode
import me.kafuuneko.rpclient.libs.security.key.RecoveryResult
import me.kafuuneko.rpclient.libs.security.key.VaultFailure
import me.kafuuneko.rpclient.libs.security.key.VaultLoadResult
import me.kafuuneko.rpclient.libs.llm.local.model.LocalModelFiles
import me.kafuuneko.rpclient.libs.room.repository.FILE_REPOSITORY_DIR_NAME
import me.kafuuneko.rpclient.libs.room.repository.FILE_REPOSITORY_STAGING_DIR_NAME
import me.kafuuneko.rpclient.libs.security.preferences.SealedPreferencesProvider
import me.kafuuneko.rpclient.libs.security.storage.ProtectedFiles
import me.kafuuneko.rpclient.libs.security.storage.RestoreJournal
import me.kafuuneko.rpclient.libs.security.storage.SetAsideKind
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget

/** Why the encrypted storage could not be opened. */
enum class StorageFailure {
    KeyFileMissing,
    KeyFileCorrupt,
    KeystoreKeyLost,
    KeystoreUnavailable,
    StorageError,

    /** A plain database from an earlier build could not be encrypted. It is still intact. */
    MigrationFailed,

    /**
     * A plain database from an earlier build is damaged, or exists twice, so it can never be
     * encrypted. It can only be exported or set aside, still in plain form.
     */
    PlainDatabaseUnusable,

    /**
     * An encrypted database does not open with its key: the file is damaged, or was encrypted
     * with another key. It can be exported or set aside, still encrypted.
     */
    DatabaseDamaged,

    /**
     * Every database opened, but a query of the startup work failed. Nothing was changed; the
     * error may pass on a retry, and the files can be exported.
     */
    StartupDatabaseError,

    /** A restore of set-aside data did not finish; restarting the app continues it. */
    RestoreIncomplete;

    companion object {
        /**
         * Failures that retrying cannot fix, the only ones where "start over" is offered. The
         * others are temporary or leave the data intact, so starting over would lose data for
         * nothing.
         */
        val START_OVER_ALLOWED: Set<StorageFailure> = setOf(KeystoreKeyLost, KeyFileMissing, KeyFileCorrupt)

        /** Failures that happen after the master key loaded, so a recovery code cannot help. */
        val AFTER_KEY_LOADED: Set<StorageFailure> =
            setOf(MigrationFailed, PlainDatabaseUnusable, DatabaseDamaged, StartupDatabaseError)

        internal fun from(failure: VaultFailure): StorageFailure = when (failure) {
            VaultFailure.KeyFileMissing -> KeyFileMissing
            VaultFailure.KeyFileCorrupt -> KeyFileCorrupt
            VaultFailure.KeystoreKeyLost -> KeystoreKeyLost
            VaultFailure.KeystoreUnavailable -> KeystoreUnavailable
            VaultFailure.StorageError -> StorageError
        }
    }
}

/** State of the encrypted storage, observed by the app gate. */
sealed class SecureStorageState {
    /** The master key is being loaded, or startup tasks are running. */
    data object Starting : SecureStorageState()

    /** Databases can be used. */
    data object Ready : SecureStorageState()

    /**
     * The user must choose how to proceed.
     *
     * @property recoveryAvailable whether a recovery code is configured
     * @property database the database that cannot be used, for the failures about one database
     */
    data class Failed(
        val failure: StorageFailure,
        val recoveryAvailable: Boolean,
        val database: EncryptedDatabase? = null
    ) : SecureStorageState()
}

/**
 * Owns the master key for the lifetime of the process and opens the encrypted storage.
 *
 * Startup sequence, off the main thread:
 * - load the master key, or create it on a fresh install
 * - encrypt plain databases left by earlier Himitsu builds
 * - release database keys to Room ([DatabaseKeySource])
 * - run the inherited startup tasks (data upgrades, file cleanup)
 *
 * Any failure stops the sequence in [SecureStorageState.Failed] without deleting anything. The
 * user then retries, enters the recovery code or starts over, and the sequence resumes.
 */
class SecureStorageManager(
    private val mContext: Context,
    private val mVault: MasterKeyVault,
    private val mMigrator: PlaintextDatabaseMigrator
) : DatabaseKeySource, FileCipherSource {
    private val mScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mMutex = Mutex()
    private val mState = MutableStateFlow<SecureStorageState>(SecureStorageState.Starting)

    /**
     * Key loaded from the vault; kept across retries so a failed migration does not reload it.
     * Written only under [mKeyLock].
     */
    @Volatile
    private var mLoadedKey: MasterKey? = null

    /** Guards loading the key, which preferences may trigger from the main thread (P-05). */
    private val mKeyLock = Any()

    /** Last load failure; preference reads do not retry the Keystore until the user does. */
    @Volatile
    private var mLastFailure: VaultFailure? = null

    /**
     * Database that cannot be used, while the state is [StorageFailure.PlainDatabaseUnusable]
     * or [StorageFailure.DatabaseDamaged].
     */
    private var mUnusableDatabase: EncryptedDatabase? = null

    /** Key released to Room once every database is encrypted. Never reset within a process. */
    private val mReleasedKey = MutableStateFlow<MasterKey?>(null)
    private var mStartupTasks: suspend () -> Unit = {}

    /** Cipher of stored files, created from [mReleasedKey] on first use. Written only under [mKeyLock]. */
    @Volatile
    private var mFileCipher: FileCipher? = null

    /** Current storage state. */
    val state: StateFlow<SecureStorageState> = mState.asStateFlow()

    /** Room open helper factory for [database]. */
    fun openHelperFactory(database: EncryptedDatabase): KeyedOpenHelperFactory =
        KeyedOpenHelperFactory(database, this)

    /**
     * Starts the storage once per process. [startupTasks] run after the databases are usable
     * and before the state becomes [SecureStorageState.Ready].
     */
    fun start(beforeKey: () -> Unit = {}, startupTasks: suspend () -> Unit) {
        // SQLCipher must be loaded explicitly, and its Java and native logging silenced before any use
        System.loadLibrary("sqlcipher")
        Logger.setTarget(NoopTarget())
        SqlCipherLog.silence()
        mStartupTasks = startupTasks
        // Cleanup that needs no key runs first, and even when the key turns out to be unavailable
        mScope.launch {
            mMutex.withLock {
                runCatching(beforeKey)
                load()
            }
        }
    }

    /** Retries after a failure that may be temporary. */
    fun retry() {
        mLastFailure = null
        launchSequence()
    }

    /**
     * Unlocks with a recovery code after the Keystore key was lost.
     *
     * @return the vault result; on success the startup sequence resumes
     */
    suspend fun recover(code: RecoveryCode): RecoveryResult = withContext(Dispatchers.IO) {
        // Keystore key generation, the key file write and the startup tasks must not run on Main
        mMutex.withLock {
            // Halfway through a restore the live key file may be the restored one: finish the swap first
            if (RestoreJournal.isPending(mContext)) return@withLock RecoveryResult.Failed(VaultFailure.StorageError)
            val result = mVault.recover(code)
            if (result is RecoveryResult.Recovered) {
                continueWith(result.masterKey)
            }
            result
        }
    }

    /**
     * Sets the key file, stored files, databases and sealed preferences aside and starts with
     * empty storage.
     *
     * Nothing is deleted: the old files keep their content under an `.unrecoverable-<time>`
     * suffix, in case the user later finds a way to recover them. The key file goes first, so
     * if a later move fails, the retry finds encrypted data without a key file and offers start
     * over again instead of opening empty databases with the old key.
     */
    fun startOver() {
        mScope.launch {
            mMutex.withLock {
                val state = mState.value as? SecureStorageState.Failed ?: return@withLock
                if (state.failure !in StorageFailure.START_OVER_ALLOWED) return@withLock
                val suffix = ".unrecoverable-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                // Decided before anything moves: a plain main database stays and is encrypted with the new key
                val primary = mContext.getDatabasePath(EncryptedDatabase.Primary.fileName)
                val primaryStaysPlain = DatabaseFiles.kindOf(primary) == DatabaseFileKind.Plaintext ||
                    (DatabaseFiles.kindOf(primary) == DatabaseFileKind.Missing && File(primary.path + ".plaintext").exists())
                // The model files go only with an encrypted registry; a plain one from an earlier
                // build stays, is encrypted with the new key and still lists them
                val registry = mContext.getDatabasePath(EncryptedDatabase.LocalModels.fileName)
                val modelsGoAside = DatabaseFiles.kindOf(registry) == DatabaseFileKind.Encrypted
                try {
                    mVault.moveKeyFileAside(suffix)
                    // Before the databases: if it fails, the retry still finds data without a key
                    if (modelsGoAside) setAsideLocalModels(suffix)
                    setAsideStoredFiles(suffix, keepPlainFiles = primaryStaysPlain)
                    EncryptedDatabase.entries.forEach { database -> setAside(mContext.getDatabasePath(database.fileName), suffix) }
                    SealedPreferencesProvider.setAside(mContext, suffix)
                    synchronized(mKeyLock) {
                        mLoadedKey?.destroy()
                        mLoadedKey = null
                        mLastFailure = null
                    }
                } catch (_: IOException) {
                    fail(StorageFailure.StorageError)
                    return@withLock
                }
                load()
            }
        }
    }

    /**
     * Sets the `FileRepository` directory (avatars, chat images, drafts) aside for "start over".
     *
     * The startup cleanup deletes every stored file the database does not reference, so with
     * the new empty database it would delete them all. An empty directory for the new storage
     * is created in its place.
     *
     * @param keepPlainFiles true when a plain main database survives and is encrypted with the
     *   new key: its plain files (from builds before P-06) stay with it, and only the sealed
     *   files, which need the lost key, go aside
     */
    private fun setAsideStoredFiles(suffix: String, keepPlainFiles: Boolean = false) {
        val directory = mContext.getDir(FILE_REPOSITORY_DIR_NAME, Context.MODE_PRIVATE)
        // An unreadable directory must not pass as empty, or the cleanup would delete its files
        directory.list() ?: throw IOException("Stored files directory is unreadable")
        // Only empty directories, as a retry after a half-finished start over finds: nothing to keep
        if (directory.walk().none { it.isFile }) return
        if (keepPlainFiles) {
            val sealed = File(directory, StoredFileLayout.SEALED_DIRECTORY)
            if (sealed.walk().none { it.isFile }) return
            val target = File(directory.path + suffix).apply { mkdirs() }
            Files.move(sealed.toPath(), File(target, StoredFileLayout.SEALED_DIRECTORY).toPath())
            return
        }
        Files.move(directory.toPath(), File(directory.path + suffix).toPath())
        File(mContext.getDir(FILE_REPOSITORY_DIR_NAME, Context.MODE_PRIVATE), FILE_REPOSITORY_STAGING_DIR_NAME).mkdirs()
    }

    /**
     * Sets a database that cannot be used aside and opens the storage without it, after the
     * user chose to.
     *
     * - A plain database that can never be encrypted goes aside in plain form, under an
     *   `.unusable-<time>` suffix, with a copy left by an interrupted migration.
     * - A damaged encrypted database goes aside still encrypted, under a `.damaged-<time>`
     *   suffix.
     * - Nothing is deleted. Setting the main database aside also sets the stored files aside,
     *   because their index lives in it.
     */
    fun setAsideUnusableDatabase() {
        mScope.launch {
            mMutex.withLock {
                val state = mState.value as? SecureStorageState.Failed ?: return@withLock
                val kind = when (state.failure) {
                    StorageFailure.PlainDatabaseUnusable -> SetAsideKind.Unusable
                    StorageFailure.DatabaseDamaged -> SetAsideKind.Damaged
                    else -> return@withLock
                }
                val database = mUnusableDatabase ?: return@withLock
                val suffix = ProtectedFiles.newSuffix(kind)
                val file = mContext.getDatabasePath(database.fileName)
                try {
                    if (database == EncryptedDatabase.Primary) setAsideStoredFiles(suffix)
                    // The new, empty registry would not list the model files: they go with it
                    if (database == EncryptedDatabase.LocalModels) setAsideLocalModels(suffix)
                    DatabaseFiles.delete(File(file.path + ".encrypting"))
                    // A plain copy left by an interrupted migration is listed as plain, never
                    // under the damaged encrypted file's marker
                    val plainCopy = File(file.path + ".plaintext")
                    val plainSuffix = if (kind == SetAsideKind.Damaged) ProtectedFiles.newSuffix(SetAsideKind.Unusable) else suffix
                    if (DatabaseFiles.anyExists(plainCopy)) DatabaseFiles.move(plainCopy, File(plainCopy.path + plainSuffix))
                    if (DatabaseFiles.anyExists(file)) DatabaseFiles.move(file, File(file.path + suffix))
                } catch (_: IOException) {
                    fail(StorageFailure.StorageError)
                    return@withLock
                }
                mUnusableDatabase = null
                load()
            }
        }
    }

    /**
     * Sets the on-device model files aside with their registry ("start over", or a registry that
     * cannot be used): a new, empty registry would not list them. They are plain GGUF files, kept
     * until the user deletes the set-aside copy.
     */
    private fun setAsideLocalModels(suffix: String) {
        val models = File(mContext.noBackupFilesDir, LocalModelFiles.DIRECTORY_NAME)
        if (!models.exists()) return
        Files.move(models.toPath(), File(models.path + suffix).toPath())
    }

    /**
     * Sets one database aside for "start over".
     *
     * - Only encrypted files are moved; a database still in plain form stays and is encrypted
     *   with the new key, so no plain copy is parked out of sight.
     * - Leftovers of an interrupted migration are settled: an incomplete `.encrypting` copy is
     *   deleted, and a verified `.plaintext` copy is put back when the live file is missing, so
     *   it is encrypted with the new key.
     */
    private fun setAside(file: File, suffix: String) {
        DatabaseFiles.delete(File(file.path + ".encrypting"))
        if (DatabaseFiles.kindOf(file) == DatabaseFileKind.Encrypted) DatabaseFiles.move(file, File(file.path + suffix))
        // A verified plain copy from an interrupted migration is the only copy readable without
        // the lost key: put it back so it is encrypted with the new key instead of deleted
        val plainCopy = File(file.path + ".plaintext")
        if (plainCopy.exists() && DatabaseFiles.kindOf(file) == DatabaseFileKind.Missing) DatabaseFiles.move(plainCopy, file)
    }

    /** Whether a recovery code is configured for the current data. */
    suspend fun hasRecoveryCode(): Boolean = withContext(Dispatchers.IO) { mMutex.withLock { mVault.hasRecoveryCode() } }

    /**
     * Creates a new recovery code, replacing any previous one. The caller shows it once and
     * wipes it.
     *
     * @return the new code, or null when the storage is not ready or the key file cannot be updated
     */
    suspend fun createRecoveryCode(): RecoveryCode? = withContext(Dispatchers.IO) {
        mMutex.withLock {
            val masterKey = mReleasedKey.value ?: return@withLock null
            val code = RecoveryCode.generate(SecureRandom())
            try {
                mVault.setRecoveryCode(masterKey, code)
                code
            } catch (_: Exception) {
                code.wipe()
                null
            }
        }
    }

    /**
     * Removes the recovery code. Afterwards a lost Keystore key means the data is lost.
     *
     * @return whether the key file was updated
     */
    suspend fun removeRecoveryCode(): Boolean = withContext(Dispatchers.IO) {
        mMutex.withLock { runCatching { mVault.removeRecoveryCode() }.isSuccess }
    }

    /**
     * Returns the key that seals preference strings, loading the master key on first use
     * (P-05). Preferences may be read on the main thread before the startup sequence has
     * loaded the key; the load is shared with that sequence and happens at most once.
     *
     * @return a new array the caller wipes, or null when the key cannot be loaded
     */
    fun preferencesKey(): ByteArray? {
        val masterKey = mLoadedKey ?: (loadMasterKeyBlocking(retryFailure = false) as? VaultLoadResult.Loaded)?.masterKey
        return masterKey?.derive(KeyPurpose.Preferences)
    }

    override fun awaitPassphrase(database: EncryptedDatabase): ByteArray {
        val masterKey = mReleasedKey.value ?: runBlocking { mReleasedKey.first { it != null } }!!
        val key = masterKey.derive(database.purpose)
        try {
            return SqlCipherKey.rawKeyPassphrase(key)
        } finally {
            key.fill(0)
        }
    }

    override fun fileCipher(): FileCipher {
        mFileCipher?.let { return it }
        val masterKey = mReleasedKey.value ?: runBlocking { mReleasedKey.first { it != null } }!!
        return synchronized(mKeyLock) {
            mFileCipher ?: FileCipher.create(masterKey.derive(KeyPurpose.Files)).also { mFileCipher = it }
        }
    }

    private fun launchSequence() {
        mScope.launch { mMutex.withLock { load() } }
    }

    /** Loads or creates the master key and continues the sequence. Called under [mMutex]. */
    private suspend fun load() {
        mLoadedKey?.let {
            continueWith(it)
            return
        }
        mState.value = SecureStorageState.Starting
        if (RestoreJournal.isPending(mContext)) {
            fail(StorageFailure.RestoreIncomplete)
            return
        }
        when (val result = loadMasterKeyBlocking(retryFailure = true)) {
            is VaultLoadResult.Loaded -> continueWith(result.masterKey)
            is VaultLoadResult.Failed -> fail(StorageFailure.from(result.failure))
        }
    }

    /**
     * Loads or creates the master key at most once per process, from any thread.
     *
     * @param retryFailure whether to try the vault again after a recorded failure; only the
     *   startup sequence does, so a broken Keystore is not hit on every preference read
     */
    private fun loadMasterKeyBlocking(retryFailure: Boolean): VaultLoadResult = synchronized(mKeyLock) {
        mLoadedKey?.let { return VaultLoadResult.Loaded(it, created = false) }
        if (!retryFailure) mLastFailure?.let { return VaultLoadResult.Failed(it) }
        // Halfway through a restore the key file may be missing on purpose: never create one
        if (RestoreJournal.isPending(mContext)) return VaultLoadResult.Failed(VaultFailure.StorageError)
        val result = mVault.loadOrCreate(hasEncryptedData = hasEncryptedData())
        when (result) {
            is VaultLoadResult.Loaded -> {
                mLoadedKey = result.masterKey
                mLastFailure = null
            }
            is VaultLoadResult.Failed -> mLastFailure = result.failure
        }
        result
    }

    /** Migrates plain databases, releases the keys and runs startup tasks. Called under [mMutex]. */
    private suspend fun continueWith(masterKey: MasterKey) {
        if (mState.value == SecureStorageState.Ready) return
        synchronized(mKeyLock) {
            mLoadedKey = masterKey
            mLastFailure = null
        }
        mState.value = SecureStorageState.Starting
        // Encrypt databases left in plain form by earlier builds, and check the encrypted ones,
        // before Room can open them
        for (database in EncryptedDatabase.entries) {
            val file = mContext.getDatabasePath(database.fileName)
            try {
                if (database.movedFromNoBackup) moveFromNoBackup(database, file)
            } catch (_: IOException) {
                fail(StorageFailure.StorageError)
                return
            }
            val key = masterKey.derive(database.purpose)
            val passphrase = SqlCipherKey.rawKeyPassphrase(key)
            key.fill(0)
            try {
                mMigrator.migrateIfNeeded(file, passphrase)
                // A damaged file would fail the first query of every screen: stop on the error
                // screen while Room has no key yet, so those queries wait instead
                if (EncryptedDatabaseCheck.check(file, passphrase) == DatabaseCheck.Damaged) {
                    mUnusableDatabase = database
                    fail(StorageFailure.DatabaseDamaged, database)
                    return
                }
            } catch (e: DatabaseMigrationException) {
                mUnusableDatabase = database.takeIf { e.permanent }
                fail(if (e.permanent) StorageFailure.PlainDatabaseUnusable else StorageFailure.MigrationFailed, database)
                return
            } finally {
                passphrase.fill(0)
            }
        }
        // Release database keys, then run the inherited startup work that needs them
        mReleasedKey.value = masterKey
        try {
            mStartupTasks()
        } catch (_: SQLiteException) {
            // Last barrier: a database error the check missed (damage deeper than its first
            // pages) shows the error screen with "try again" instead of crashing at every start
            fail(StorageFailure.StartupDatabaseError)
            return
        }
        mState.value = SecureStorageState.Ready
    }

    /** Moves a database that earlier builds kept in `noBackupFilesDir` next to the others, once. */
    private fun moveFromNoBackup(database: EncryptedDatabase, file: File) {
        val legacy = File(mContext.noBackupFilesDir, database.fileName)
        // The main file moves last, so while it is still at its source the move is unfinished
        if (!legacy.exists() || file.exists()) return
        file.parentFile?.mkdirs()
        DatabaseFiles.move(legacy, file)
    }

    private fun fail(failure: StorageFailure, database: EncryptedDatabase? = null) {
        mState.value = SecureStorageState.Failed(failure, recoveryAvailable = mVault.hasRecoveryCode(), database = database)
    }

    /**
     * Whether any database, preference or stored file is already encrypted, so a missing key
     * file must not be replaced.
     */
    private fun hasEncryptedData(): Boolean = EncryptedDatabase.entries.any { database ->
        DatabaseFiles.kindOf(mContext.getDatabasePath(database.fileName)) == DatabaseFileKind.Encrypted
    } || SealedPreferencesProvider.hasSealedFiles(mContext) || StoredFileLayout.hasSealedFiles(mContext)
}
