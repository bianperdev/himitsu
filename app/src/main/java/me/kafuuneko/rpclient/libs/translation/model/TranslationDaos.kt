// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.model

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import me.kafuuneko.rpclient.libs.room.MutableDao

/** Translation services configured by the user. */
@Dao
interface TranslationProviderDao : MutableDao<TranslationProvider> {
    @Query("SELECT * FROM translation_providers ORDER BY id ASC")
    suspend fun getAll(): List<TranslationProvider>

    @Query("SELECT * FROM translation_providers WHERE id = :id")
    suspend fun getById(id: Long): TranslationProvider?

    @Query("DELETE FROM translation_providers WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/** Display texts of chat messages. */
@Dao
interface ChatMessageTranslationDao {
    @Query("SELECT * FROM chat_message_translations WHERE messageId IN (:messageIds)")
    suspend fun getByMessageIds(messageIds: List<Long>): List<ChatMessageTranslation>

    @Upsert
    suspend fun upsert(translation: ChatMessageTranslation)

    @Query("DELETE FROM chat_message_translations WHERE messageId = :messageId")
    suspend fun deleteByMessageId(messageId: Long)
}

/** Display texts of group chat messages. */
@Dao
interface GroupChatMessageTranslationDao {
    @Query("SELECT * FROM group_chat_message_translations WHERE messageId IN (:messageIds)")
    suspend fun getByMessageIds(messageIds: List<Long>): List<GroupChatMessageTranslation>

    @Upsert
    suspend fun upsert(translation: GroupChatMessageTranslation)

    @Query("DELETE FROM group_chat_message_translations WHERE messageId = :messageId")
    suspend fun deleteByMessageId(messageId: Long)
}

/** Per-chat translation settings. */
@Dao
interface ChatTranslationSettingDao {
    @Query("SELECT * FROM chat_translation_settings WHERE sessionId = :sessionId")
    suspend fun get(sessionId: Long): ChatTranslationSetting?

    @Upsert
    suspend fun upsert(setting: ChatTranslationSetting)
}

/** Per-group-chat translation settings. */
@Dao
interface GroupChatTranslationSettingDao {
    @Query("SELECT * FROM group_chat_translation_settings WHERE sessionId = :sessionId")
    suspend fun get(sessionId: Long): GroupChatTranslationSetting?

    @Upsert
    suspend fun upsert(setting: GroupChatTranslationSetting)
}
