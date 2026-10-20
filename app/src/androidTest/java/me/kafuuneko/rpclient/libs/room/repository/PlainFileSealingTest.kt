// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.room.repository

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.FileEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Himitsu (P-06): stored files are sealed, and plain files of earlier builds are sealed without
 * ever losing the only readable copy. Uses placeholder bytes in an isolated directory.
 */
@RunWith(AndroidJUnit4::class)
class PlainFileSealingTest {
    private lateinit var database: AppDatabase
    private lateinit var files: FileRepository
    private lateinit var directory: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(base.cacheDir, "plain-sealing-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getDir(name: String, mode: Int): File = File(directory, name).apply { mkdirs() }
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        files = FileRepository(context, database, TestFileCipher)
    }

    @After
    fun tearDown() {
        database.close()
        directory.deleteRecursively()
    }

    @Test
    fun newFilesAreStoredSealed() = runBlocking {
        val bytes = placeholder("new file")
        val uuid = files.saveFile(File(directory, "source").apply { writeBytes(bytes) })

        val sealed = TestFileCipher.sealedFile(directory, sha256(bytes))
        assertTrue(sealed.isFile)
        assertEquals("HMF1", sealed.readBytes().copyOf(4).decodeToString())
        assertFalse(contains(sealed.readBytes(), bytes))
        assertFalse(File(directory, "repository/${sha256(bytes)}").exists())
        assertTrue(bytes.contentEquals(files.readBytes(uuid)))
    }

    @Test
    fun aPlainFileIsReadableBeforeAndAfterItIsSealed() = runBlocking {
        val bytes = placeholder("legacy file")
        val (uuid, plain) = seedPlain(bytes)

        assertTrue(bytes.contentEquals(files.readBytes(uuid)))
        assertEquals(0, files.sealPlainFiles())

        assertFalse(plain.exists())
        assertTrue(TestFileCipher.sealedFile(directory, sha256(bytes)).isFile)
        assertTrue(bytes.contentEquals(files.readBytes(uuid)))
    }

    @Test
    fun everyInterruptedStepEndsWithOneVerifiedCopy() = runBlocking {
        // Crash after the rename and before deleting the plain file
        val second = placeholder("interrupted before delete")
        val (secondUuid, secondPlain) = seedPlain(second)
        files.sealPlainFiles()
        secondPlain.writeBytes(second)
        // Crash while sealing: an incomplete copy is left next to the plain file
        val first = placeholder("interrupted while sealing")
        val (firstUuid, firstPlain) = seedPlain(first)
        val firstSealed = TestFileCipher.sealedFile(directory, sha256(first))
        File(firstSealed.path + ".sealing").writeBytes(ByteArray(100))
        // A sealed copy damaged on disk must be replaced, not trusted
        val third = placeholder("damaged sealed copy")
        val (thirdUuid, thirdPlain) = seedPlain(third)
        val thirdSealed = TestFileCipher.sealedFile(directory, sha256(third)).apply { writeBytes(ByteArray(300)) }

        assertEquals(0, files.sealPlainFiles())

        assertFalse(firstPlain.exists() || secondPlain.exists() || thirdPlain.exists())
        assertFalse(File(firstSealed.path + ".sealing").exists())
        assertTrue(thirdSealed.isFile)
        assertTrue(first.contentEquals(files.readBytes(firstUuid)))
        assertTrue(second.contentEquals(files.readBytes(secondUuid)))
        assertTrue(third.contentEquals(files.readBytes(thirdUuid)))
    }

    @Test
    fun aPlainFileThatDoesNotMatchItsNameIsKept() = runBlocking {
        val bytes = placeholder("damaged plain file")
        val (uuid, plain) = seedPlain(bytes)
        plain.writeBytes(placeholder("other content"))

        assertEquals(1, files.sealPlainFiles())

        assertTrue(plain.isFile)
        assertFalse(TestFileCipher.sealedFile(directory, sha256(bytes)).exists())
        assertNotNull(files.readBytes(uuid))
    }

    @Test
    fun aFileBeingReadIsSealedLater() = runBlocking {
        val bytes = placeholder("file being read")
        val (uuid, plain) = seedPlain(bytes)
        val reading = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val reader = async {
            files.withFileLease(uuid) {
                reading.complete(Unit)
                release.await()
                it.readBytes()
            }
        }
        withTimeout(5_000) { reading.await() }

        assertEquals(1, files.sealPlainFiles())
        assertTrue(plain.isFile)
        release.complete(Unit)
        assertTrue(bytes.contentEquals(reader.await()))
        assertEquals(0, files.sealPlainFiles())
        assertFalse(plain.exists())
    }

    @Test
    fun aPlainDraftIsSealedUnderItsHandle() = runBlocking {
        val bytes = placeholder("legacy draft")
        val handle = UUID.randomUUID().toString()
        val legacy = File(directory, "repository/staging").apply { mkdirs() }
        File(legacy, handle).writeBytes(bytes)
        File(legacy, "$handle.meta").outputStream().use { output ->
            Properties().apply {
                setProperty("owner", "draft")
                setProperty("hash", sha256(bytes))
                setProperty("size", bytes.size.toString())
                setProperty("mime", "image/png")
            }.store(output, null)
        }

        assertEquals(0, files.sealPlainFiles())

        assertTrue(legacy.listFiles().orEmpty().isEmpty())
        val restored = requireNotNull(files.restorePrepared("draft", handle))
        assertEquals(sha256(bytes), restored.file.hash)
        assertTrue(bytes.contentEquals(files.withPreparedFile(restored) { it.readBytes() }))
        val uuid = files.mutate(listOf(restored)) { restored.file.uuid }
        assertTrue(bytes.contentEquals(files.readBytes(uuid)))
    }

    @Test
    fun cleanupRemovesOnlySealedFilesThatNothingReferences() = runBlocking {
        val kept = placeholder("referenced")
        val keptUuid = files.saveFile(File(directory, "kept").apply { writeBytes(kept) })
        val orphan = TestFileCipher.sealedFile(directory, "c".repeat(64)).apply { writeBytes(ByteArray(64)) }
        val prepared = files.prepareStream("draft", ByteArrayInputStream(placeholder("draft")), "image/png")

        files.cleanupAbandonedFiles()

        assertFalse(orphan.exists())
        assertTrue(kept.contentEquals(files.readBytes(keptUuid)))
        assertTrue(files.withPreparedFile(prepared) { it.length } > 0)
        files.releasePrepared(prepared)
    }

    private suspend fun seedPlain(bytes: ByteArray): Pair<String, File> {
        val hash = sha256(bytes)
        val plain = File(directory, "repository/$hash").apply {
            parentFile!!.mkdirs()
            writeBytes(bytes)
        }
        val uuid = UUID.randomUUID().toString()
        database.getFileDao().insert(FileEntity(uuid, hash, "image/png"))
        return uuid to plain
    }

    private fun placeholder(text: String): ByteArray =
        ByteArray(70_000) { index -> text[index % text.length].code.toByte() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        val window = needle.copyOf(32)
        return (0..haystack.size - window.size).any { start ->
            window.indices.all { haystack[start + it] == window[it] }
        }
    }
}
