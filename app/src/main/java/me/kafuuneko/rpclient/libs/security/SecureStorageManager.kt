// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import android.content.Context
import java.io.File
import java.io.IOException
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
import me.kafuuneko.rpclient.libs.security.database.DatabaseFileKind
import me.kafuuneko.rpclient.libs.security.database.DatabaseFiles
import me.kafuuneko.rpclient.libs.security.database.DatabaseKeySource
import me.kafuuneko.rpclient.libs.security.database.DatabaseMigrationException
import me.kafuuneko.rpclient.libs.security.database.EncryptedDatabase
import me.kafuuneko.rpclient.libs.security.database.KeyedOpenHelperFactory
import me.kafuuneko.rpclient.libs.security.database.PlaintextDatabaseMigrator
import me.kafuuneko.rpclient.libs.security.database.SqlCipherKey
import me.kafuuneko.rpclient.libs.security.key.KeyPurpose
import me.kafuuneko.rpclient.libs.security.key.MasterKey
import me.kafuuneko.rpclient.libs.security.key.MasterKeyVault
import me.kafuuneko.rpclient.libs.security.key.RecoveryCode
import me.kafuuneko.rpclient.libs.security.key.RecoveryResult
import me.kafuuneko.rpclient.libs.security.key.VaultFailure
import me.kafuuneko.rpclient.libs.security.key.VaultLoadResult
import me.kafuuneko.rpclient.libs.security.preferences.SealedPreferencesProvider
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
    MigrationFailed;

    companion object {
        /**
         * Failures that retrying cannot fix, the only ones where "start over" is offered. The
         * others are temporary or leave the data intact, so starting over would lose data for
         * nothing.
         */
        val START_OVER_ALLOWED: Set<StorageFailure> = setOf(KeystoreKeyLost, KeyFileMissing, KeyFileCorrupt)

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

    /** The user must choose how to proceed. [recoveryAvailable] tells whether a code can help. */
    data class Failed(val failure: StorageFailure, val recoveryAvailable: Boolean) : SecureStorageState()
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
) : DatabaseKeySource {
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

    /** Key released to Room once every database is encrypted. Never reset within a process. */
    private val mReleasedKey = MutableStateFlow<MasterKey?>(null)
    private var mStartupTasks: suspend () -> Unit = {}

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
        // SQLCipher must be loaded explicitly, and its Java logging silenced before any use
        System.loadLibrary("sqlcipher")
        Logger.setTarget(NoopTarget())
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
            val result = mVault.recover(code)
            if (result is RecoveryResult.Recovered) {
                continueWith(result.masterKey)
            }
            result
        }
    }

    /**
     * Sets the current databases and key file aside and starts with empty storage.
     *
     * Nothing is deleted: the old files keep their content under an `.unrecoverable-<time>`
     * suffix, in case the user later finds a way to recover them.
     */
    fun startOver() {
        mScope.launch {
            mMutex.withLock {
                val state = mState.value as? SecureStorageState.Failed ?: return@withLock
                if (state.failure !in StorageFailure.START_OVER_ALLOWED) return@withLock
                val suffix = ".unrecoverable-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                try {
                    EncryptedDatabase.entries.forEach { database -> setAside(mContext.getDatabasePath(database.fileName), suffix) }
                    mVault.moveKeyFileAside(suffix)
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
        // Encrypt databases left in plain form by earlier builds before Room can open them
        try {
            EncryptedDatabase.entries.forEach { database ->
                val key = masterKey.derive(database.purpose)
                val passphrase = SqlCipherKey.rawKeyPassphrase(key)
                key.fill(0)
                try {
                    mMigrator.migrateIfNeeded(mContext.getDatabasePath(database.fileName), passphrase)
                } finally {
                    passphrase.fill(0)
                }
            }
        } catch (_: DatabaseMigrationException) {
            fail(StorageFailure.MigrationFailed)
            return
        }
        // Release database keys, then run the inherited startup work that needs them
        mReleasedKey.value = masterKey
        mStartupTasks()
        mState.value = SecureStorageState.Ready
    }

    private fun fail(failure: StorageFailure) {
        mState.value = SecureStorageState.Failed(failure, recoveryAvailable = mVault.hasRecoveryCode())
    }

    /** Whether any database or preference is already encrypted, so a missing key file must not be replaced. */
    private fun hasEncryptedData(): Boolean = EncryptedDatabase.entries.any { database ->
        DatabaseFiles.kindOf(mContext.getDatabasePath(database.fileName)) == DatabaseFileKind.Encrypted
    } || SealedPreferencesProvider.hasSealedFiles(mContext)
}
