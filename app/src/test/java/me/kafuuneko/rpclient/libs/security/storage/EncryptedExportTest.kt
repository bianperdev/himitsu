// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.storage

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import java.util.zip.ZipInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses random placeholder bytes only. */
class EncryptedExportTest {
    private val directory: File = Files.createTempDirectory("export-test").toFile()

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun filesAreCopiedByteForByteAndVanishedFilesAreSkipped() {
        val first = File(directory, "a").apply { writeBytes(ByteArray(200_000).also(SecureRandom()::nextBytes)) }
        val second = File(directory, "b").apply { writeBytes(ByteArray(10).also(SecureRandom()::nextBytes)) }
        val output = ByteArrayOutputStream()

        EncryptedExport.write(
            listOf(
                ExportEntry("databases/a", first),
                ExportEntry("no_backup/gone", File(directory, "gone")),
                ExportEntry("app_repository/sealed/b", second)
            ),
            output
        )

        val read = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                read[entry.name] = zip.readBytes()
            }
        }
        assertEquals(listOf("databases/a", "app_repository/sealed/b"), read.keys.toList())
        assertTrue(read.getValue("databases/a").contentEquals(first.readBytes()))
        assertTrue(read.getValue("app_repository/sealed/b").contentEquals(second.readBytes()))
    }
}
