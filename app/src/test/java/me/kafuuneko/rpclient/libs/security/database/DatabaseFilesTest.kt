// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.database

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses placeholder bytes only. */
class DatabaseFilesTest {
    private val directory: File = Files.createTempDirectory("database-files").toFile()

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun anInterruptedMoveKeepsTheWalWithItsDatabase() {
        val database = File(directory, "primary.sqlite").apply { writeText("main") }
        File(database.path + "-wal").writeText("wal")
        File(database.path + "-shm").writeText("shm")
        val target = File(directory, "primary.sqlite.replaced-20261020-120000")
        // A crash after the companions moved: the database is still at its source
        File(database.path + "-wal").renameTo(File(target.path + "-wal"))

        assertTrue(DatabaseFiles.anyExists(database))
        DatabaseFiles.move(database, target)

        assertFalse(DatabaseFiles.anyExists(database))
        assertEquals("main", target.readText())
        assertEquals("wal", File(target.path + "-wal").readText())
        assertEquals("shm", File(target.path + "-shm").readText())
    }
}
