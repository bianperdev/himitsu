package me.kafuuneko.rpclient.libs.room.repository

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.room.AppDatabase
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileRepositoryThumbnailTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: FileRepository
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        cacheDir = context.cacheDir
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FileRepository(context, database, TestFileCipher)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun largeSquareImage_isSampledAndScaledToRequestedBounds() = runBlocking {
        val uuid = saveBitmap(width = 2048, height = 2048)

        val thumbnail = repository.loadSampledBitmap(uuid, 64, 64)

        assertTrue(thumbnail != null)
        assertTrue(requireNotNull(thumbnail).width <= 64)
        assertTrue(thumbnail.height <= 64)
        assertTrue(thumbnail.byteCount <= 64 * 64 * 4)
    }

    @Test
    fun wideAndTallImages_preserveAspectRatioWithinBounds() = runBlocking {
        val wide = repository.loadSampledBitmap(saveBitmap(2048, 256), 80, 80)
        val tall = repository.loadSampledBitmap(saveBitmap(256, 2048), 80, 80)

        assertTrue(requireNotNull(wide).width <= 80 && wide.height <= 80)
        assertTrue(wide.width > wide.height)
        assertTrue(requireNotNull(tall).width <= 80 && tall.height <= 80)
        assertTrue(tall.height > tall.width)
    }

    @Test
    fun invalidSizeAndCorruptFile_returnNull() {
        runBlocking {
            val corrupt = File.createTempFile("corrupt-avatar", ".bin", cacheDir).apply {
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            val uuid = repository.saveFile(corrupt, "application/octet-stream")

            assertNull(repository.loadSampledBitmap(uuid, 64, 64))
            assertNull(repository.loadSampledBitmap(uuid, 0, 64))
            assertNull(repository.loadSampledBitmap(uuid, 64, -1))
            corrupt.delete()
        }
    }

    private suspend fun saveBitmap(width: Int, height: Int): String {
        val file = File.createTempFile("avatar", ".png", cacheDir)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
        return repository.saveFile(file, "image/png").also { file.delete() }
    }
}
