// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
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
import me.kafuuneko.rpclient.libs.security.database.DatabaseFileKind
import me.kafuuneko.rpclient.libs.security.database.DatabaseFiles
import me.kafuuneko.rpclient.libs.security.database.PlaintextDatabaseMigrator
import me.kafuuneko.rpclient.libs.security.key.KeyWrapper
import me.kafuuneko.rpclient.libs.security.key.KeyWrapperException
import me.kafuuneko.rpclient.libs.security.key.KeyWrapperFailure
import me.kafuuneko.rpclient.libs.security.key.MasterKeyFileStore
import me.kafuuneko.rpclient.libs.security.key.MasterKeyVault
import me.kafuuneko.rpclient.libs.security.key.VaultLoadResult
import me.kafuuneko.rpclient.libs.security.storage.MASTER_KEY_FILE_NAME
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Himitsu: "start over" after a lost Keystore key keeps every stored file. Runs the real
 * storage manager in an isolated directory, with an in-memory key wrapper that loses its keys
 * when replaced. Uses placeholder text and random keys only.
 */
@RunWith(AndroidJUnit4::class)
class StartOverAndroidTest {
    private lateinit var root: File
    private lateinit var context: Context
    private val random = SecureRandom()
    private val sealedName = "a".repeat(64)
    private val plainName = "b".repeat(64)

    @Before
    fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(base.cacheDir, "start-over-${UUID.randomUUID()}").apply { mkdirs() }
        val info = ApplicationInfo(base.applicationInfo).apply { dataDir = root.path }
        context = object : ContextWrapper(base) {
            override fun getApplicationInfo(): ApplicationInfo = info
            override fun getDatabasePath(name: String): File = File(root, "databases/$name").apply { parentFile!!.mkdirs() }
            override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
            override fun getDir(name: String, mode: Int): File = File(root, "app_$name").apply { mkdirs() }
        }
        // A key file whose Keystore key exists only in this first wrapper
        val store = MasterKeyFileStore(File(context.noBackupFilesDir, MASTER_KEY_FILE_NAME))
        (MasterKeyVault(store, InMemoryKeyWrapper(), random).loadOrCreate(hasEncryptedData = false) as VaultLoadResult.Loaded)
            .masterKey.destroy()
        File(root, "app_repository/sealed/$sealedName").apply { parentFile!!.mkdirs() }.writeText("sealed image")
        File(root, "app_repository/$plainName").writeText("plain image")
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun startingOverSetsEveryStoredFileAside() = runBlocking {
        val manager = startWithLostKey()

        manager.startOver()
        withTimeout(TIMEOUT) { manager.state.first { it == SecureStorageState.Ready } }

        val aside = setAsideDirectory()
        assertEquals("sealed image", File(aside, "sealed/$sealedName").readText())
        assertEquals("plain image", File(aside, plainName).readText())
        assertTrue(File(root, "app_repository").walk().none { it.isFile })
    }

    @Test
    fun aSurvivingPlainDatabaseKeepsItsPlainFiles() = runBlocking {
        System.loadLibrary("sqlcipher")
        val primary = context.getDatabasePath("primary.sqlite")
        SQLiteDatabase.openOrCreateDatabase(primary.path, "", null, null).use { database ->
            database.execSQL("CREATE TABLE placeholder (value TEXT)")
        }
        val manager = startWithLostKey()

        manager.startOver()
        withTimeout(TIMEOUT) { manager.state.first { it == SecureStorageState.Ready } }

        // The plain database was encrypted with the new key and its plain image stayed with it;
        // the sealed image needs the lost key, so it went aside
        assertEquals(DatabaseFileKind.Encrypted, DatabaseFiles.kindOf(primary))
        assertEquals("plain image", File(root, "app_repository/$plainName").readText())
        assertEquals("sealed image", File(setAsideDirectory(), "sealed/$sealedName").readText())
        assertFalse(File(root, "app_repository/sealed/$sealedName").exists())
    }

    @Test
    fun retryingAfterAHalfFinishedStartOverSetsNoEmptyDirectoryAside() = runBlocking {
        File(root, "app_repository").deleteRecursively()
        File(root, "app_repository/staging").mkdirs()
        val manager = startWithLostKey()

        manager.startOver()
        withTimeout(TIMEOUT) { manager.state.first { it == SecureStorageState.Ready } }

        assertTrue(root.listFiles().orEmpty().none { it.name.startsWith("app_repository.unrecoverable-") })
    }

    /** A manager whose key wrapper no longer has the key of the existing key file. */
    private suspend fun startWithLostKey(): SecureStorageManager {
        val store = MasterKeyFileStore(File(context.noBackupFilesDir, MASTER_KEY_FILE_NAME))
        val manager = SecureStorageManager(context, MasterKeyVault(store, InMemoryKeyWrapper(), random), PlaintextDatabaseMigrator())
        manager.start(startupTasks = {})
        val failed = withTimeout(TIMEOUT) { manager.state.first { it is SecureStorageState.Failed } }
        assertEquals(StorageFailure.KeystoreKeyLost, (failed as SecureStorageState.Failed).failure)
        return manager
    }

    private fun setAsideDirectory(): File =
        root.listFiles().orEmpty().single { it.name.startsWith("app_repository.unrecoverable-") }

    /** Keeps wrapping keys in memory; a new instance has lost every key of the previous one. */
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
            val key = mKeys[keyId] ?: throw KeyWrapperException(KeyWrapperFailure.KeyLost)
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
