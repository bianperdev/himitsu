// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.SecureRandom
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import me.kafuuneko.rpclient.libs.security.database.DatabaseFileKind
import me.kafuuneko.rpclient.libs.security.database.DatabaseFiles
import me.kafuuneko.rpclient.libs.security.database.DatabaseMigrationException
import me.kafuuneko.rpclient.libs.security.database.EncryptedDatabase
import me.kafuuneko.rpclient.libs.security.database.KeyedOpenHelperFactory
import me.kafuuneko.rpclient.libs.security.database.PlaintextDatabaseMigrator
import me.kafuuneko.rpclient.libs.security.database.SqlCipherKey
import me.kafuuneko.rpclient.libs.security.key.AndroidKeystoreKeyWrapper
import me.kafuuneko.rpclient.libs.security.key.KeyWrapperException
import me.kafuuneko.rpclient.libs.security.key.KeyWrapperFailure
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the parts of encrypted storage that need a real device: the Android Keystore,
 * SQLCipher and Room. Keys are random per test and never printed.
 */
@RunWith(AndroidJUnit4::class)
class SecureStorageAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val random = SecureRandom()
    private val dir by lazy { File(context.cacheDir, "secure-storage-test").apply { mkdirs() } }

    @Before
    fun loadSqlCipher() {
        System.loadLibrary("sqlcipher")
        Logger.setTarget(NoopTarget())
    }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
        AndroidKeystoreKeyWrapper(TEST_ALIAS).deleteKey(TEST_KEY_ID)
    }

    @Test
    fun keystoreWrapperSealsAndReportsALostKey() {
        val wrapper = AndroidKeystoreKeyWrapper(TEST_ALIAS)
        val secret = ByteArray(32).also(random::nextBytes)
        val aad = byteArrayOf(1, 2, 3)

        val box = wrapper.seal(TEST_KEY_ID, secret, aad)
        val opened = wrapper.open(TEST_KEY_ID, box, aad)
        wrapper.deleteKey(TEST_KEY_ID)

        assertTrue(opened.contentEquals(secret))
        try {
            wrapper.open(TEST_KEY_ID, box, aad)
            fail("A deleted Keystore key must not open the box")
        } catch (e: KeyWrapperException) {
            assertEquals(KeyWrapperFailure.KeyLost, e.failure)
        }
    }

    @Test
    fun plainDatabaseIsMigratedAndVerified() {
        val file = File(dir, "plain.sqlite")
        createPlainDatabase(file, rows = 250)
        val passphrase = newPassphrase()

        val migrated = PlaintextDatabaseMigrator().migrateIfNeeded(file, passphrase)

        assertTrue(migrated)
        assertEquals(DatabaseFileKind.Encrypted, DatabaseFiles.kindOf(file))
        assertFalse(File(file.path + ".plaintext").exists())
        assertFalse(File(file.path + ".encrypting").exists())
        SQLiteDatabase.openDatabase(file.path, passphrase, null, SQLiteDatabase.OPEN_READONLY, null, null).use { db ->
            assertEquals(7, db.version)
            db.rawQuery("SELECT COUNT(*) FROM notes", emptyArray<String>()).use {
                it.moveToFirst()
                assertEquals(250, it.getInt(0))
            }
        }
        assertFalse(PlaintextDatabaseMigrator().migrateIfNeeded(file, passphrase))
        try {
            SQLiteDatabase.openDatabase(file.path, newPassphrase(), null, SQLiteDatabase.OPEN_READONLY, null, null).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM sqlite_master", emptyArray<String>()).use { it.moveToFirst() }
            }
            fail("Another key must not open the database")
        } catch (_: android.database.sqlite.SQLiteException) {
            // Expected: the file is unreadable without its key
        }
    }

    @Test
    fun interruptedSwapIsRolledBackAndMigrated() {
        val file = File(dir, "interrupted.sqlite")
        createPlainDatabase(file, rows = 3)
        // Simulate a crash after the original was renamed but before the copy took its place
        DatabaseFiles.move(file, File(file.path + ".plaintext"))
        val passphrase = newPassphrase()

        PlaintextDatabaseMigrator().migrateIfNeeded(file, passphrase)

        assertEquals(DatabaseFileKind.Encrypted, DatabaseFiles.kindOf(file))
        assertFalse(File(file.path + ".plaintext").exists())
    }

    @Test
    fun aDamagedPlainDatabaseIsReportedAsPermanentAndKept() {
        val file = File(dir, "damaged.sqlite")
        val damaged = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(4096) { 0x5a }
        file.writeBytes(damaged)

        try {
            PlaintextDatabaseMigrator().migrateIfNeeded(file, newPassphrase())
            fail("A damaged database must not migrate")
        } catch (e: DatabaseMigrationException) {
            assertTrue(e.permanent)
        }
        assertTrue(file.readBytes().contentEquals(damaged))
        assertFalse(File(file.path + ".encrypting").exists())
    }

    @Test
    fun twoPlainCopiesAreReportedAsPermanentAndBothKept() {
        val file = File(dir, "twice.sqlite")
        createPlainDatabase(file, rows = 2)
        createPlainDatabase(File(file.path + ".plaintext"), rows = 3)

        try {
            PlaintextDatabaseMigrator().migrateIfNeeded(file, newPassphrase())
            fail("Two plain copies must not be merged or dropped")
        } catch (e: DatabaseMigrationException) {
            assertTrue(e.permanent)
        }
        assertEquals(DatabaseFileKind.Plaintext, DatabaseFiles.kindOf(file))
        assertEquals(DatabaseFileKind.Plaintext, DatabaseFiles.kindOf(File(file.path + ".plaintext")))
    }

    @Test
    fun roomOpensAnEncryptedDatabaseThroughTheDeferredFactory() {
        val file = File(dir, "room.sqlite")
        val passphrase = newPassphrase()
        val factory = KeyedOpenHelperFactory(EncryptedDatabase.RequestLog) { passphrase.copyOf() }

        val database = Room.databaseBuilder(context, RequestLogDatabase::class.java, file.path)
            .openHelperFactory(factory)
            .build()
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO llm_request_logs (createTime, providerName, providerType, protocol, model, " +
                "isStreaming, requestJson, responseJson) VALUES (1, 'p', 'Custom', 'OpenAI', 'm', 0, '{}', '{}')"
        )
        database.close()

        assertEquals(DatabaseFileKind.Encrypted, DatabaseFiles.kindOf(file))
        val reopened = Room.databaseBuilder(context, RequestLogDatabase::class.java, file.path)
            .openHelperFactory(factory)
            .build()
        reopened.openHelper.readableDatabase.query("SELECT COUNT(*) FROM llm_request_logs").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        reopened.close()
    }

    private fun newPassphrase(): ByteArray {
        val key = ByteArray(32).also(random::nextBytes)
        return SqlCipherKey.rawKeyPassphrase(key).also { key.fill(0) }
    }

    /** Creates a plain database in WAL mode with rows still in the WAL file. */
    private fun createPlainDatabase(file: File, rows: Int) {
        val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)
        db.enableWriteAheadLogging()
        db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY, body TEXT NOT NULL)")
        db.version = 7
        repeat(rows) { db.execSQL("INSERT INTO notes (body) VALUES (?)", arrayOf("note $it")) }
        db.close()
    }

    private companion object {
        const val TEST_ALIAS = "himitsu_test_wrap"
        const val TEST_KEY_ID = "0011223344556677"
    }
}
