// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.storage

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import me.kafuuneko.rpclient.libs.security.preferences.PreferenceCipher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Himitsu: a restore swaps the data in use with a set-aside copy and survives a crash at any
 * point. Runs in an isolated directory with placeholder text only.
 */
@RunWith(AndroidJUnit4::class)
class RestoreJournalTest {
    private lateinit var root: File
    private lateinit var context: Context
    private val source = ".unrecoverable-20261020-120000"
    private val replaced = ".replaced-20261020-130000"

    @Before
    fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(base.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        val info = ApplicationInfo(base.applicationInfo).apply { dataDir = root.path }
        context = object : ContextWrapper(base) {
            override fun getApplicationInfo(): ApplicationInfo = info
            override fun getDatabasePath(name: String): File = File(root, "databases/$name")
            override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        }
        // The data in use
        write("databases/primary.sqlite", "live db")
        write("databases/primary.sqlite-wal", "live wal")
        write("no_backup/$MASTER_KEY_FILE_NAME", "live key")
        write("app_repository/sealed/a", "live image")
        write("shared_prefs/AppModel.xml", "<string>${PreferenceCipher.PREFIX}live</string>")
        write("shared_prefs/AppModel.xml.bak", "live backup")
        write("shared_prefs/himitsu_security.xml", "<boolean>plain</boolean>")
        // The copy set aside, and its key sealed again for the restore
        write("databases/primary.sqlite$source", "copy db")
        write("databases/primary.sqlite$source-wal", "copy wal")
        write("no_backup/$MASTER_KEY_FILE_NAME$source", "copy original key")
        write("no_backup/$MASTER_KEY_FILE_NAME${SetAsideRestorer.RESTORED_SUFFIX}", "copy key")
        write("app_repository$source/sealed/b", "copy image")
        write("shared_prefs/AppModel.xml$source", "copy prefs")
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun theCopyTakesThePlaceOfTheDataInUseAndNothingIsLost() {
        assertTrue(RestoreJournal.schedule(context, source, replaced))
        assertTrue(RestoreJournal.isPending(context))

        assertTrue(RestoreJournal.completePending(context))

        assertFalse(RestoreJournal.isPending(context))
        assertCopyInPlaceAndLiveDataAside()
    }

    @Test
    fun aCrashHalfwayThroughPhaseOneIsFinishedOnTheNextStart() {
        assertTrue(RestoreJournal.schedule(context, source, replaced))
        // The process died after the WAL and the key moved, before the database did
        rename("databases/primary.sqlite-wal", "databases/primary.sqlite$replaced-wal")
        rename("no_backup/$MASTER_KEY_FILE_NAME", "no_backup/$MASTER_KEY_FILE_NAME$replaced")

        assertTrue(RestoreJournal.completePending(context))

        assertCopyInPlaceAndLiveDataAside()
    }

    @Test
    fun aTakenDestinationStopsTheSwapAndKeepsTheJournal() {
        assertTrue(RestoreJournal.schedule(context, source, replaced))
        write("databases/primary.sqlite$replaced", "something else")

        assertFalse(RestoreJournal.completePending(context))

        assertTrue(RestoreJournal.isPending(context))
        assertEquals("live db", read("databases/primary.sqlite"))
        assertEquals("something else", read("databases/primary.sqlite$replaced"))
    }

    private fun assertCopyInPlaceAndLiveDataAside() {
        assertEquals("copy db", read("databases/primary.sqlite"))
        assertEquals("copy wal", read("databases/primary.sqlite-wal"))
        assertEquals("copy key", read("no_backup/$MASTER_KEY_FILE_NAME"))
        assertEquals("copy image", read("app_repository/sealed/b"))
        assertEquals("copy prefs", read("shared_prefs/AppModel.xml"))
        assertFalse(File(root, "shared_prefs/AppModel.xml.bak").exists())
        assertEquals("<boolean>plain</boolean>", read("shared_prefs/himitsu_security.xml"))
        assertEquals("live db", read("databases/primary.sqlite$replaced"))
        assertEquals("live wal", read("databases/primary.sqlite$replaced-wal"))
        assertEquals("live key", read("no_backup/$MASTER_KEY_FILE_NAME$replaced"))
        assertEquals("live image", read("app_repository$replaced/sealed/a"))
        assertEquals("live backup", read("shared_prefs/AppModel.xml.bak$replaced"))
        assertEquals("copy original key", read("no_backup/$MASTER_KEY_FILE_NAME$source"))
    }

    private fun write(path: String, text: String) {
        File(root, path).apply { parentFile!!.mkdirs() }.writeText(text)
    }

    private fun read(path: String): String = File(root, path).readText()

    private fun rename(from: String, to: String) {
        assertTrue(File(root, from).renameTo(File(root, to)))
    }
}
