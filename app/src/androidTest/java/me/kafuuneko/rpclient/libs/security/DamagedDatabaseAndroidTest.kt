// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.database.sqlite.SQLiteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.libs.security.database.EncryptedDatabase
import me.kafuuneko.rpclient.libs.security.database.PlaintextDatabaseMigrator
import me.kafuuneko.rpclient.libs.security.database.SqlCipherKey
import me.kafuuneko.rpclient.libs.security.key.KeyWrapper
import me.kafuuneko.rpclient.libs.security.key.MasterKeyFileStore
import me.kafuuneko.rpclient.libs.security.key.MasterKeyVault
import me.kafuuneko.rpclient.libs.security.key.VaultLoadResult
import me.kafuuneko.rpclient.libs.security.storage.MASTER_KEY_FILE_NAME
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Himitsu: an encrypted database that does not open with its key stops the startup on the
 * error screen, never crashes the app and is set aside without being deleted. Runs the real
 * storage manager in an isolated directory, with an in-memory key wrapper that keeps its keys.
 * Uses placeholder text and random keys only.
 */
@RunWith(AndroidJUnit4::class)
class DamagedDatabaseAndroidTest {
    private lateinit var root: File
    private lateinit var context: Context
    private lateinit var store: MasterKeyFileStore
    private val random = SecureRandom()
    private val wrapper = InMemoryKeyWrapper()
    private val sealedName = "a".repeat(64)

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(base.cacheDir, "damaged-database-${UUID.randomUUID()}").apply { mkdirs() }
        val info = ApplicationInfo(base.applicationInfo).apply { dataDir = root.path }
        context = object : ContextWrapper(base) {
            override fun getApplicationInfo(): ApplicationInfo = info
            override fun getDatabasePath(name: String): File = File(root, "databases/$name").apply { parentFile!!.mkdirs() }
            override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
            override fun getDir(name: String, mode: Int): File = File(root, "app_$name").apply { mkdirs() }
        }
        store = MasterKeyFileStore(File(context.noBackupFilesDir, MASTER_KEY_FILE_NAME))
        File(root, "app_repository/sealed/$sealedName").apply { parentFile!!.mkdirs() }.writeText("sealed image")
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun aDamagedDatabaseStopsOnTheErrorScreenAndIsSetAsideStillIntact() = runBlocking {
        createKeyFile()
        val primary = context.getDatabasePath(EncryptedDatabase.Primary.fileName)
        val damaged = ByteArray(8192).also(random::nextBytes)
        primary.writeBytes(damaged)
        var startupRan = false
        val manager = newManager()

        manager.start(startupTasks = { startupRan = true })
        val failed = withTimeout(TIMEOUT) { manager.state.first { it is SecureStorageState.Failed } }

        // The startup stopped before Room got the keys, so nothing queried the damaged file
        assertEquals(StorageFailure.DatabaseDamaged, (failed as SecureStorageState.Failed).failure)
        assertFalse(startupRan)

        manager.setAsideUnusableDatabase()
        withTimeout(TIMEOUT) { manager.state.first { it == SecureStorageState.Ready } }

        // The damaged file and the images it indexed went aside byte for byte, still encrypted
        val databases = File(root, "databases").listFiles().orEmpty()
        assertArrayEquals(damaged, databases.single { it.name.startsWith("primary.sqlite.damaged-") }.readBytes())
        assertFalse(primary.exists())
        val asideRepository = root.listFiles().orEmpty().single { it.name.startsWith("app_repository.damaged-") }
        assertEquals("sealed image", File(asideRepository, "sealed/$sealedName").readText())
        assertTrue(startupRan)
    }

    @Test
    fun aHealthyDatabaseInWalModeStartsNormally() = runBlocking {
        val key = createKeyFile()
        val primary = context.getDatabasePath(EncryptedDatabase.Primary.fileName)
        val passphrase = SqlCipherKey.rawKeyPassphrase(key)
        SQLiteDatabase.openDatabase(primary.path, passphrase, null, SQLiteDatabase.CREATE_IF_NECESSARY, null, null).use { database ->
            // Room keeps its databases in WAL mode, which a read-only check must still open
            database.rawQuery("PRAGMA journal_mode = WAL", emptyArray<String>()).use { it.moveToFirst() }
            database.execSQL("CREATE TABLE placeholder (value TEXT)")
            database.execSQL("INSERT INTO placeholder VALUES ('placeholder')")
        }
        passphrase.fill(0)
        key.fill(0)
        val manager = newManager()

        manager.start(startupTasks = {})

        assertEquals(SecureStorageState.Ready, withTimeout(TIMEOUT) { manager.state.first { it != SecureStorageState.Starting } })
        assertTrue(primary.exists())
    }

    @Test
    fun aDatabaseErrorInTheStartupTasksShowsTheErrorScreenInsteadOfCrashing() = runBlocking {
        createKeyFile()
        val manager = newManager()

        manager.start(startupTasks = { throw SQLiteException("placeholder failure") })
        val failed = withTimeout(TIMEOUT) { manager.state.first { it is SecureStorageState.Failed } }

        assertEquals(StorageFailure.StartupDatabaseError, (failed as SecureStorageState.Failed).failure)
    }

    @Test
    fun aDamagedRequestLogNamesItsDatabaseAndKeepsTheChats() = runBlocking {
        createKeyFile()
        val requestLog = context.getDatabasePath(EncryptedDatabase.RequestLog.fileName)
        requestLog.writeBytes(ByteArray(8192).also(random::nextBytes))
        val manager = newManager()

        manager.start(startupTasks = {})
        val failed = withTimeout(TIMEOUT) { manager.state.first { it is SecureStorageState.Failed } } as SecureStorageState.Failed

        // The error screen can tell the user that only the debug request log is affected
        assertEquals(StorageFailure.DatabaseDamaged, failed.failure)
        assertEquals(EncryptedDatabase.RequestLog, failed.database)

        manager.setAsideUnusableDatabase()
        withTimeout(TIMEOUT) { manager.state.first { it == SecureStorageState.Ready } }

        // Only the request log went aside: the images indexed by the main database stayed
        assertTrue(File(root, "databases").listFiles().orEmpty().any { it.name.startsWith("request_logs.sqlite.damaged-") })
        assertEquals("sealed image", File(root, "app_repository/sealed/$sealedName").readText())
        assertTrue(root.listFiles().orEmpty().none { it.name.startsWith("app_repository.damaged-") })
    }

    /** Creates the key file and returns the primary database key; the caller wipes it. */
    private fun createKeyFile(): ByteArray {
        val masterKey = (MasterKeyVault(store, wrapper, random).loadOrCreate(hasEncryptedData = false) as VaultLoadResult.Loaded).masterKey
        try {
            return masterKey.derive(EncryptedDatabase.Primary.purpose)
        } finally {
            masterKey.destroy()
        }
    }

    private fun newManager(): SecureStorageManager =
        SecureStorageManager(context, MasterKeyVault(store, wrapper, random), PlaintextDatabaseMigrator())

    /** Keeps wrapping keys in memory for the whole test, like a working Keystore. */
    private inner class InMemoryKeyWrapper : KeyWrapper {
        private val mKeys = mutableMapOf<String, ByteArray>()

        override fun seal(keyId: String, plaintext: ByteArray, aad: ByteArray): ByteArray {
            val key = mKeys.getOrPut(keyId) { ByteArray(32).also(random::nextBytes) }
            val iv = ByteArray(12).also(random::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(aad)
            return iv + cipher.doFinal(plaintext)
        }

        override fun open(keyId: String, box: ByteArray, aad: ByteArray): ByteArray {
            val key = mKeys.getValue(keyId)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, box, 0, 12))
            cipher.updateAAD(aad)
            return cipher.doFinal(box, 12, box.size - 12)
        }
    }

    private companion object {
        const val TIMEOUT = 15_000L
    }
}
