// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.core

import me.kafuuneko.rpclient.libs.translation.FakeTranslationClient
import me.kafuuneko.rpclient.libs.translation.LanguageDetector
import me.kafuuneko.rpclient.libs.translation.TranslationClient
import me.kafuuneko.rpclient.libs.translation.TranslationClientFactory
import me.kafuuneko.rpclient.libs.translation.TranslationProviderConfig
import me.kafuuneko.rpclient.libs.translation.model.ChatMessageTranslation
import me.kafuuneko.rpclient.libs.translation.model.ChatMessageTranslationDao
import me.kafuuneko.rpclient.libs.translation.model.ChatTranslationSetting
import me.kafuuneko.rpclient.libs.translation.model.ChatTranslationSettingDao
import me.kafuuneko.rpclient.libs.translation.model.GroupChatMessageTranslation
import me.kafuuneko.rpclient.libs.translation.model.GroupChatMessageTranslationDao
import me.kafuuneko.rpclient.libs.translation.model.GroupChatTranslationSetting
import me.kafuuneko.rpclient.libs.translation.model.GroupChatTranslationSettingDao
import me.kafuuneko.rpclient.libs.translation.model.TranslationProvider
import me.kafuuneko.rpclient.libs.translation.model.TranslationProviderDao

// In-memory stand-ins for the Room DAOs and the services: unit tests run without Room.

class InMemoryTranslationProviderDao : TranslationProviderDao {
    val rows = linkedMapOf<Long, TranslationProvider>()

    override suspend fun getAll(): List<TranslationProvider> = rows.values.toList()
    override suspend fun getById(id: Long): TranslationProvider? = rows[id]
    override suspend fun deleteById(id: Long) { rows.remove(id) }
    override suspend fun insertOrReplace(data: TranslationProvider): Long {
        val id = if (data.id == 0L) (rows.keys.maxOrNull() ?: 0L) + 1 else data.id
        rows[id] = data.copy(id = id)
        return id
    }
    override suspend fun insertOrReplaceAll(data: List<TranslationProvider>): List<Long> = data.map { insertOrReplace(it) }
    override suspend fun insertOrIgnore(data: TranslationProvider): Long =
        if (rows.containsKey(data.id)) -1L else insertOrReplace(data)
    override suspend fun insertOrIgnoreAll(data: List<TranslationProvider>): List<Long> = data.map { insertOrIgnore(it) }
    override suspend fun update(data: TranslationProvider) { if (rows.containsKey(data.id)) rows[data.id] = data }
    override suspend fun delete(data: TranslationProvider) { rows.remove(data.id) }
}

class InMemoryChatMessageTranslationDao : ChatMessageTranslationDao {
    val rows = linkedMapOf<Long, ChatMessageTranslation>()
    var upserts = 0

    override suspend fun getByMessageIds(messageIds: List<Long>): List<ChatMessageTranslation> = messageIds.mapNotNull { rows[it] }
    override suspend fun upsert(translation: ChatMessageTranslation) { upserts++; rows[translation.messageId] = translation }
    override suspend fun deleteByMessageId(messageId: Long) { rows.remove(messageId) }
}

class InMemoryGroupChatMessageTranslationDao : GroupChatMessageTranslationDao {
    val rows = linkedMapOf<Long, GroupChatMessageTranslation>()

    override suspend fun getByMessageIds(messageIds: List<Long>): List<GroupChatMessageTranslation> = messageIds.mapNotNull { rows[it] }
    override suspend fun upsert(translation: GroupChatMessageTranslation) { rows[translation.messageId] = translation }
    override suspend fun deleteByMessageId(messageId: Long) { rows.remove(messageId) }
}

class InMemoryChatTranslationSettingDao : ChatTranslationSettingDao {
    val rows = linkedMapOf<Long, ChatTranslationSetting>()

    override suspend fun get(sessionId: Long): ChatTranslationSetting? = rows[sessionId]
    override suspend fun upsert(setting: ChatTranslationSetting) { rows[setting.sessionId] = setting }
}

class InMemoryGroupChatTranslationSettingDao : GroupChatTranslationSettingDao {
    val rows = linkedMapOf<Long, GroupChatTranslationSetting>()

    override suspend fun get(sessionId: Long): GroupChatTranslationSetting? = rows[sessionId]
    override suspend fun upsert(setting: GroupChatTranslationSetting) { rows[setting.sessionId] = setting }
}

/** Device settings held in fields. */
class FakeCoreSettings(
    override var enabled: Boolean = true,
    override var defaultProviderId: Long? = 1L,
    override var translateOwnMessages: Boolean = true,
    var language: String = "es"
) : TranslationCoreSettings {
    override fun userLanguage(): String = language
}

/** Returns a fixed answer and counts the calls. */
class StubLanguageDetector(var answer: String?) : LanguageDetector {
    var calls = 0

    override suspend fun detect(text: String): String? {
        calls++
        return answer
    }
}

/**
 * Wraps [FakeTranslationClient], recording each request; [translateOverride] and [detectAnswer]
 * change its behaviour.
 */
class RecordingClientFactory : TranslationClientFactory {
    val translateCalls = mutableListOf<Triple<List<String>, String?, String>>()
    var detectCalls = 0
    var detectAnswer: String? = null
    var translateOverride: (suspend (List<String>) -> List<String>)? = null

    override fun create(config: TranslationProviderConfig): TranslationClient = object : TranslationClient {
        private val fake = FakeTranslationClient()

        override suspend fun translate(texts: List<String>, source: String?, target: String): List<String> {
            translateCalls += Triple(texts, source, target)
            return translateOverride?.invoke(texts) ?: fake.translate(texts, source, target)
        }

        override suspend fun detect(text: String): String? {
            detectCalls++
            return detectAnswer
        }
    }
}
