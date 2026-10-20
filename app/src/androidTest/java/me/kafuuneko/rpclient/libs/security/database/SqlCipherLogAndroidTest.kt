// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.database

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that SQLite errors never reach Logcat: the app silences SQLCipher's native logs at
 * startup ([SqlCipherLog]). A probe marker proves the test can read this process's log at all.
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherLogAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file = File(context.cacheDir, "sqlcipher-log-test.db")

    @After
    fun cleanUp() {
        listOf("", "-journal", "-wal", "-shm").forEach { File(file.path + it).delete() }
    }

    @Test
    fun sqliteErrorsAreNotLogged() {
        // The application loaded SQLCipher and silenced it at startup
        assertTrue(SqlCipherLog.isSilenced)
        val table = "himitsu_log_probe_" + UUID.randomUUID().toString().replace("-", "")
        val database = SQLiteDatabase.openDatabase(
            file.path, "", null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY, null
        )
        try {
            runCatching { database.rawQuery("SELECT * FROM $table", emptyArray<String>()).use { it.moveToFirst() } }
        } finally {
            database.close()
        }
        val marker = "himitsu-log-marker-" + UUID.randomUUID()
        Log.w(TAG, marker)
        val log = readOwnLog()
        assertTrue("This process's Logcat could not be read", log.contains(marker))
        assertFalse(log.contains(table))
    }

    /** Waits until the marker is flushed, then returns everything this process logged. */
    private fun readOwnLog(): String {
        Thread.sleep(LOG_FLUSH_MILLIS)
        val process = ProcessBuilder("logcat", "-d", "--pid=${android.os.Process.myPid()}")
            .redirectErrorStream(true)
            .start()
        return process.inputStream.bufferedReader().use { it.readText() }
    }

    private companion object {
        const val TAG = "HimitsuLogTest"
        const val LOG_FLUSH_MILLIS = 500L
    }
}
