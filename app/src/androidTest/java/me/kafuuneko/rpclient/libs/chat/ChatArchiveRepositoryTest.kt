package me.kafuuneko.rpclient.libs.chat

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.media.MessageImageRuntime
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.model.MessageType
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.TestFileCipher
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.MessageImageRepository
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatArchiveRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ChatArchiveRepository
    private var characterId: Long = 0L
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var files: FileRepository

    @Before
    fun setUp() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(base.cacheDir, "archive-test-${System.nanoTime()}").apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getDir(name: String, mode: Int) = File(directory, name).apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        files = FileRepository(context, database, TestFileCipher)
        repository = ChatArchiveRepository(
            mContext = context,
            mAppDatabase = database,
            mCodec = ChatArchiveCodec(Gson()),
            mFiles = files,
            mImages = MessageImageRuntime(context, files),
            mImageStore = ChatArchiveImageStore(context)
        )
        characterId = database.getCharacterDao().insertOrReplace(
            Character(
                name = "Target",
                avatar = "",
                characterTags = "[]",
                description = "",
                personality = "",
                scenario = "",
                firstMessages = "",
                examplesOfDialogue = "",
                postHistoryInstructions = ""
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
        directory.deleteRecursively()
    }

    /** 导入图片后分支保留角色及附件顺序，任一侧删除都不能破坏另一侧的共享文件。 */
    @Test
    fun importedImagesBranchWithIndependentReferencesForEveryRole() = runBlocking {
        val bytes = imageBytes(Bitmap.CompressFormat.PNG)
        val chats = ChatRepository(database, Gson(), MessageImageRepository(database, files))
        for (role in ChatArchiveMessageRole.entries) for (deleteSourceFirst in listOf(true, false)) {
            // 走真实导入入口，包括角色与旁白，避免只验证用户图片的既有路径。
            val image = ChatArchiveImage("image/png", Base64.getEncoder().encodeToString(bytes))
            val original = repository.saveImport(archive().copy(messages = listOf(
                ChatArchiveMessage(1_000, role, "", listOf(image, image))
            ), summary = null), characterId)
            val source = chats.getAllChatMessagesBySessionId(original.sessionId).single()
            val sourceImages = chats.getMessagesWithImages(listOf(source.id)).single().images
            val branch = chats.createBranchSession(original.sessionId, source.id, "branch")
            val copied = chats.getAllChatMessagesBySessionId(branch).single()
            val copiedImages = chats.getMessagesWithImages(listOf(copied.id)).single().images
            assertEquals(source.source, copied.source)
            assertEquals(listOf(0, 1), copiedImages.map { it.image.position })
            val allUuids = (sourceImages + copiedImages).map { it.image.imageUuid }
            assertEquals(4, allUuids.toSet().size)
            assertEquals(1, (sourceImages + copiedImages).map { it.file!!.hash }.toSet().size)

            // 分别删除原会话和分支，验证独立引用及最后一个引用的文件回收。
            chats.deleteSession(if (deleteSourceFirst) original.sessionId else branch)
            val surviving = if (deleteSourceFirst) copiedImages else sourceImages
            surviving.forEach { attachment ->
                files.withFileLease(attachment.image.imageUuid) { assertArrayEquals(bytes, it.readBytes()) }
            }
            chats.deleteSession(if (deleteSourceFirst) branch else original.sessionId)
            assertFalse(TestFileCipher.sealedFile(directory, surviving.first().file!!.hash).exists())
        }
    }

    @Test
    fun confirmedCharacterIsUsedAndSummaryBoundaryUsesNewMessageId() = runBlocking {
        val sessionId = repository.saveImport(archive(), characterId).sessionId
        val session = database.getChatSessionDao().getSessionById(sessionId)
        val messages = database.getChatMessageDao().getMessagesBySessionId(sessionId)
        val summary = database.getChatMessageDao().getLatestSummaryBySessionId(sessionId)

        assertEquals(characterId, session?.characterId)
        assertEquals("[]", session?.lorebookEntrySet)
        assertEquals("{}", session?.worldInfoStateJson)
        assertEquals(
            listOf(ChatMessage.Source.User, ChatMessage.Source.Char, ChatMessage.Source.System),
            messages.map { it.source }
        )
        assertEquals(messages[1].id, summary?.coveredMessageId)
        assertEquals("Summary", summary?.content)
    }

    @Test
    fun missingCharacterLeavesNoPartialSession() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.saveImport(archive(), Long.MAX_VALUE)
            }
        }
        runBlocking {
            assertEquals(emptyList<Any>(), database.getChatSessionDao().getAllSessions())
        }
    }

    @Test
    fun exportStreamsMultiplePagesInStableOrderAndPreservesSummaryBoundary() = runBlocking {
        val archive = largeArchive()
        val sessionId = repository.saveImport(archive, characterId).sessionId
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val exportFile = File.createTempFile("chat-export-", ".jsonl", context.cacheDir)

        try {
            repository.exportToUri(sessionId, Uri.fromFile(exportFile))
            val decoded = exportFile.reader(Charsets.UTF_8).use { reader ->
                ChatArchiveCodec(Gson()).decode(reader, fallbackTitle = "Fallback")
            }

            assertEquals(archive.messages.map { it.content }, decoded.messages.map { it.content })
            assertEquals(archive.messages.map { it.role }, decoded.messages.map { it.role })
            assertEquals(archive.summary?.coveredMessageIndex, decoded.summary?.coveredMessageIndex)
        } finally {
            exportFile.delete()
        }
    }

    /** 真正写入文件仓库并导出、重新导入，验证图片字节、顺序与纯图正文。 */
    @Test
    fun privateImagesRoundTripWithoutRecompressionOrDatabasePayloads() = runBlocking {
        val first = imageBytes(Bitmap.CompressFormat.PNG)
        val second = imageBytes(Bitmap.CompressFormat.JPEG)
        val input = archive().copy(messages = listOf(
            ChatArchiveMessage(1_000, ChatArchiveMessageRole.User, "", listOf(
                ChatArchiveImage("image/png", Base64.getEncoder().encodeToString(first)),
                ChatArchiveImage("image/jpeg", Base64.getEncoder().encodeToString(second))
            )),
            ChatArchiveMessage(2_000, ChatArchiveMessageRole.Character, "picture", listOf(
                ChatArchiveImage("image/png", Base64.getEncoder().encodeToString(first))
            ))
        ), summary = null)
        val original = repository.saveImport(input, characterId)
        val target = File(context.cacheDir, "roundtrip.jsonl")
        assertEquals(0, repository.exportToUri(original.sessionId, Uri.fromFile(target)))
        val parsed = repository.readImportFromUri(Uri.fromFile(target))
        try {
            assertEquals("", parsed.messages.first().content)
            assertTrue(parsed.messages.flatMap { it.images }.all { it.data == null && it.resourceKey != null })
            val restored = repository.saveImport(parsed, characterId)
            assertEquals(0, restored.skippedImages)
            val messages = database.getChatMessageDao().getMessagesBySessionId(restored.sessionId)
            val images = database.getMessageImageDao().getByMessage(MessageType.Single, messages.first().id)
            assertEquals(listOf(0, 1), images.map { it.position })
            assertArrayEquals(first, files.withFileLease(images[0].imageUuid) { it.readBytes() })
            assertArrayEquals(second, files.withFileLease(images[1].imageUuid) { it.readBytes() })
            assertEquals(1, database.getMessageImageDao().getByMessage(MessageType.Single, messages[1].id).size)
        } finally {
            repository.releaseImport(parsed)
        }
        assertTrue(File(context.cacheDir, "chat-archive-images").listFiles().orEmpty().isEmpty())
    }

    /** 同消息内重复和跨分页重复只输出一次载荷，恢复后每个位置仍有独立 UUID。 */
    @Test
    fun compressedHashReferencesRoundTripAcrossPagesWithIndependentAttachmentOwnership() = runBlocking {
        // JPEG 尾部填充保持原始字节，可验证归档封装没有重编码或截断文件。
        val bytes = imageBytes(Bitmap.CompressFormat.JPEG) + ByteArray(64 * 1024)
        val encoded = ChatArchiveImagePayload.encode(ChatArchiveImage("image/jpeg",
            Base64.getEncoder().encodeToString(bytes)))
        assertEquals("gzip", encoded.compression)
        val reference = ChatArchiveImage(hash = encoded.hash)
        val input = archive().copy(messages = List(300) { index ->
            ChatArchiveMessage(index + 1L, ChatArchiveMessageRole.User, "message-$index",
                if (index == 0) listOf(encoded, reference) else listOf(reference))
        }, summary = null)
        val original = repository.saveImport(input, characterId)
        val target = File(context.cacheDir, "deduplicated.jsonl")
        assertEquals(0, repository.exportToUri(original.sessionId, Uri.fromFile(target)))
        val entries = target.readLines().drop(1).flatMap { line ->
            val extension = JsonParser.parseString(line).asJsonObject.getAsJsonObject("extra")
                .getAsJsonObject("rpclient")
            assertEquals(1, extension["schema_version"].asInt)
            extension.getAsJsonArray("images").map { it.asJsonObject }
        }
        assertEquals(301, entries.size)
        assertEquals(1, entries.count { it.has("data") })
        assertEquals("gzip", entries.first()["compression"].asString)
        assertTrue(entries.drop(1).all { it.keySet() == setOf("hash") })

        // 暂存和 PreparedFile 均按唯一资源复用，只有数据库引用按附件位置增加。
        val parsed = repository.readImportFromUri(Uri.fromFile(target))
        try {
            assertEquals(1, parsed.messages.flatMap { it.images }.map { it.resourceKey }.distinct().size)
            val ownerDirectory = File(context.cacheDir, "chat-archive-images/${parsed.importOwner}")
            assertEquals(1, ownerDirectory.listFiles().orEmpty().size)
            val restored = repository.saveImport(parsed, characterId)
            assertEquals(0, restored.skippedImages)
            val messages = database.getChatMessageDao().getMessagesBySessionId(restored.sessionId)
            val attachments = database.getMessageImageDao().getByMessages(MessageType.Single, messages.map { it.id })
            assertEquals(301, attachments.map { it.imageUuid }.distinct().size)
            assertEquals(listOf(0, 1), attachments.take(2).map { it.position })
            assertArrayEquals(bytes, files.withFileLease(attachments.last().imageUuid) { it.readBytes() })
            assertTrue(attachments.all { files.getFileEntity(it.imageUuid)?.hash == encoded.hash })
            assertEquals(1, File(directory, "repository/sealed").listFiles().orEmpty().count { it.isFile })
            // 删除首条消息的所有权后，后续引用仍然可读。
            files.mutate {
                database.getMessageImageDao().deleteByMessage(MessageType.Single, messages.first().id)
                removeFiles(attachments.take(2).map { it.imageUuid })
            }
            assertArrayEquals(bytes, files.withFileLease(attachments.last().imageUuid) { it.readBytes() })
        } finally {
            repository.releaseImport(parsed)
        }
        assertTrue(File(context.cacheDir, "chat-archive-images").listFiles().orEmpty().isEmpty())
    }

    /** 本地恰好已有同 hash 也不能补全归档的前向引用；只绑定本次此前出现的数据。 */
    @Test
    fun forwardAndCrossArchiveHashReferencesAreSkippedEvenWhenLocalFileExists() = runBlocking {
        val payload = ChatArchiveImagePayload.encode(ChatArchiveImage("image/png",
            Base64.getEncoder().encodeToString(imageBytes(Bitmap.CompressFormat.PNG))))
        val message = ChatArchiveMessage(1, ChatArchiveMessageRole.User, "original", listOf(payload))
        repository.saveImport(archive().copy(messages = listOf(message), summary = null), characterId)
        val hash = requireNotNull(payload.hash)
        val definition = Gson().toJson(mapOf("hash" to hash, "mime_type" to payload.mimeType,
            "compression" to payload.compression, "data" to payload.data))
        val target = File(context.cacheDir, "forward.jsonl").apply {
            writeText("""{"chat_metadata":{}}
                {"mes":"original","extra":{"rpclient":{"schema_version":1,"images":[{"hash":"$hash"},$definition,{"hash":"$hash"}]}}}
            """.trimIndent())
        }
        // 解析后的未命中项不能在保存时被后面的定义回填。
        val parsed = repository.readImportFromUri(Uri.fromFile(target))
        try {
            val result = repository.saveImport(parsed, characterId)
            assertEquals(1, result.skippedImages)
            val restored = database.getChatMessageDao().getMessagesBySessionId(result.sessionId).single()
            assertEquals("original", restored.content)
            val images = database.getMessageImageDao().getByMessage(MessageType.Single, restored.id)
            assertEquals(listOf(0, 1), images.map { it.position })
            assertFalse(images[0].imageUuid == images[1].imageUuid)
        } finally {
            repository.releaseImport(parsed)
        }
    }

    /** 解压后 hash 错配和事务失败都不能留下唯一文件或重复引用的部分提交。 */
    @Test
    fun invalidCompressedHashAndFailedReferenceCommitLeaveNoResources() = runBlocking {
        val payload = ChatArchiveImagePayload.encode(ChatArchiveImage("image/jpeg",
            Base64.getEncoder().encodeToString(imageBytes(Bitmap.CompressFormat.JPEG) + ByteArray(8192))))
        val message = ChatArchiveMessage(1, ChatArchiveMessageRole.User, "", listOf(payload,
            ChatArchiveImage(hash = payload.hash)))
        val input = archive().copy(messages = listOf(message), summary = null)
        for ((candidate, character) in listOf(
            input.copy(messages = listOf(message.copy(images = listOf(payload.copy(hash = "0".repeat(64)))))) to characterId,
            input to Long.MAX_VALUE
        )) {
            // 失败发生在文件准备之后，必须检查数据库和物理目录两个存储边界。
            assertTrue(runCatching { repository.saveImport(candidate, character) }.isFailure)
            assertTrue(database.getChatSessionDao().getAllSessions().isEmpty())
            assertTrue(File(directory, "repository").walk().none { it.isFile })
            assertTrue(TestFileCipher.stagingDirectory(directory).listFiles().orEmpty().isEmpty())
        }
    }

    /** 前一张已解码而后一张损坏时，解析失败必须删除本次全部暂存。 */
    @Test
    fun malformedArchiveAfterStagingImageReleasesImportDirectory() = runBlocking {
        val data = Base64.getEncoder().encodeToString(imageBytes(Bitmap.CompressFormat.PNG))
        val target = File(context.cacheDir, "broken.jsonl").apply {
            writeText("""{"chat_metadata":{}}
                {"mes":"","extra":{"rpclient":{"schema_version":1,"images":[{"mime_type":"image/png","data":"$data"},{"mime_type":"image/png","data":"!!!"}]}}}
            """.trimIndent())
        }
        // 通过真实解析入口验证所有者清理，不能仅断言解码异常。
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.readImportFromUri(Uri.fromFile(target)) }
        }
        assertTrue(File(context.cacheDir, "chat-archive-images").listFiles().orEmpty().isEmpty())
        assertTrue(database.getChatSessionDao().getAllSessions().isEmpty())
    }

    /** 外部缺图不生成占位文件或引用，成功匹配的图片才建立附件。 */
    @Test
    fun externalImageImportSkipsMissingReferencesAndKeepsText() = runBlocking {
        val source = File(context.cacheDir, "source.png").apply { writeBytes(imageBytes(Bitmap.CompressFormat.PNG)) }
        val input = archive().copy(messages = listOf(ChatArchiveMessage(
            1_000, ChatArchiveMessageRole.User, "Keep this text", listOf(
                ChatArchiveImage(sourceUrl = "user/images/A/missing.png"),
                ChatArchiveImage(sourceUrl = "user/images/A/source.png")
            )
        )), summary = null)
        val result = repository.saveImport(input, characterId,
            mapOf("user/images/A/source.png" to Uri.fromFile(source)))
        assertEquals(1, result.skippedImages)
        val message = database.getChatMessageDao().getMessagesBySessionId(result.sessionId).single()
        assertEquals("Keep this text", message.content)
        val images = database.getMessageImageDao().getByMessage(MessageType.Single, message.id)
        assertEquals(1, images.size)
        assertEquals(0, images.single().position)
        assertArrayEquals(source.readBytes(), files.withFileLease(images.single().imageUuid) { it.readBytes() })
    }

    /** 准备成功一张后另一张损坏、或最终角色不存在时，文件与会话均不能部分提交。 */
    @Test
    fun invalidEmbeddedDataAndFailedCommitLeaveNoPartialImport() = runBlocking {
        val valid = ChatArchiveImage("image/png", Base64.getEncoder().encodeToString(imageBytes(Bitmap.CompressFormat.PNG)))
        val input = archive().copy(messages = listOf(ChatArchiveMessage(1_000,
            ChatArchiveMessageRole.User, "", listOf(valid))), summary = null)
        for ((candidate, id) in listOf(
            input.copy(messages = listOf(input.messages.single().copy(images = listOf(valid,
                ChatArchiveImage("image/png", "bm90IGFuIGltYWdl"))))) to characterId,
            input to Long.MAX_VALUE
        )) {
            assertTrue(runCatching { repository.saveImport(candidate, id) }.isFailure)
            assertTrue(database.getChatSessionDao().getAllSessions().isEmpty())
            assertTrue(File(directory, "repository").walk().none { it.isFile })
            assertTrue(TestFileCipher.stagingDirectory(directory).listFiles().orEmpty().isEmpty())
        }
    }

    /** 已丢失的物理图片导出时跳过，不向正文追加不可逆的遗漏标记。 */
    @Test
    fun missingStoredImageIsSkippedDuringExport() = runBlocking {
        val input = archive().copy(messages = listOf(ChatArchiveMessage(1_000,
            ChatArchiveMessageRole.User, "original", listOf(ChatArchiveImage("image/png",
                Base64.getEncoder().encodeToString(imageBytes(Bitmap.CompressFormat.PNG)))))), summary = null)
        val imported = repository.saveImport(input, characterId)
        val message = database.getChatMessageDao().getMessagesBySessionId(imported.sessionId).single()
        val image = database.getMessageImageDao().getByMessage(MessageType.Single, message.id).single()
        files.withFileLease(image.imageUuid) { it.deleteForTest() }
        val target = File(context.cacheDir, "missing.jsonl")
        assertEquals(1, repository.exportToUri(imported.sessionId, Uri.fromFile(target)))
        val decoded = ChatArchiveCodec(Gson()).decode(target.readText(), "Missing")
        assertEquals("original", decoded.messages.single().content)
        assertTrue(decoded.messages.single().images.isEmpty())
    }

    private fun imageBytes(format: Bitmap.CompressFormat): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        return try {
            ByteArrayOutputStream().use { output -> bitmap.compress(format, 90, output); output.toByteArray() }
        } finally {
            bitmap.recycle()
        }
    }

    private fun archive(): ChatArchive {
        return ChatArchive(
            title = "Imported",
            createTime = 1_000L,
            latestTime = 3_000L,
            userName = "Alice",
            userDescription = "",
            userNote = "",
            creatorNotes = null,
            lorebookEntrySet = "[999]",
            worldInfoStateJson = """{"unsafe":true}""",
            autoSummaryPaused = true,
            characterNameHint = "Source",
            characterFingerprint = null,
            messages = listOf(
                ChatArchiveMessage(1_000L, ChatArchiveMessageRole.User, "Hello"),
                ChatArchiveMessage(2_000L, ChatArchiveMessageRole.Character, "Welcome"),
                ChatArchiveMessage(3_000L, ChatArchiveMessageRole.Narrator, "Rain")
            ),
            summary = ChatArchiveSummary(
                content = "Summary",
                createTime = 4_000L,
                coveredMessageIndex = 1
            )
        )
    }

    private fun largeArchive(): ChatArchive {
        val messages = List(300) { index ->
            ChatArchiveMessage(
                createTime = 1_000L + index / 3,
                role = when (index % 3) {
                    0 -> ChatArchiveMessageRole.User
                    1 -> ChatArchiveMessageRole.Character
                    else -> ChatArchiveMessageRole.Narrator
                },
                content = "Message $index"
            )
        }
        return archive().copy(
            latestTime = messages.last().createTime,
            messages = messages,
            summary = ChatArchiveSummary(
                content = "Large summary",
                createTime = messages.last().createTime + 1L,
                coveredMessageIndex = 257
            )
        )
    }
}
