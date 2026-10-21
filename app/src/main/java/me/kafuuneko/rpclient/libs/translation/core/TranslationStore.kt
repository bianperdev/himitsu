// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.core

import me.kafuuneko.rpclient.libs.translation.TranslationSessionKey
import me.kafuuneko.rpclient.libs.translation.model.ChatMessageTranslation
import me.kafuuneko.rpclient.libs.translation.model.ChatMessageTranslationDao
import me.kafuuneko.rpclient.libs.translation.model.ChatTranslationSetting
import me.kafuuneko.rpclient.libs.translation.model.ChatTranslationSettingDao
import me.kafuuneko.rpclient.libs.translation.model.GroupChatMessageTranslation
import me.kafuuneko.rpclient.libs.translation.model.GroupChatMessageTranslationDao
import me.kafuuneko.rpclient.libs.translation.model.GroupChatTranslationSetting
import me.kafuuneko.rpclient.libs.translation.model.GroupChatTranslationSettingDao
import me.kafuuneko.rpclient.libs.translation.model.TranslationMode

/** A chat's translation settings, whichever the session kind. */
internal data class SessionSetting(
    val mode: TranslationMode = TranslationMode.Auto,
    val conversationLanguage: String? = null,
    val providerId: Long? = null
)

/** A message's stored display text, whichever the session kind. */
internal data class StoredTranslation(
    val messageId: Long,
    val displayText: String,
    val displayLanguage: String,
    val conversationLanguage: String,
    val contentHash: String,
    val providerId: Long?,
    val createTime: Long
) {
    override fun toString(): String = "StoredTranslation(messageId=$messageId, [REDACTED])"
}

/**
 * Routes reads and writes to the one-to-one or group tables by [TranslationSessionKey], so
 * [ChatTranslatorImpl] has a single code path for both session kinds.
 */
internal class TranslationStore(
    private val chatMessages: ChatMessageTranslationDao,
    private val groupMessages: GroupChatMessageTranslationDao,
    private val chatSettings: ChatTranslationSettingDao,
    private val groupSettings: GroupChatTranslationSettingDao
) {
    /** The chat's settings; a chat without a row uses the defaults (Auto). */
    suspend fun setting(session: TranslationSessionKey): SessionSetting = when (session) {
        is TranslationSessionKey.Chat -> chatSettings.get(session.sessionId)
            ?.let { SessionSetting(it.mode, it.conversationLanguage, it.providerId) }
        is TranslationSessionKey.GroupChat -> groupSettings.get(session.sessionId)
            ?.let { SessionSetting(it.mode, it.conversationLanguage, it.providerId) }
    } ?: SessionSetting()

    suspend fun saveSetting(session: TranslationSessionKey, setting: SessionSetting) {
        when (session) {
            is TranslationSessionKey.Chat -> chatSettings.upsert(
                ChatTranslationSetting(session.sessionId, setting.mode, setting.conversationLanguage, setting.providerId)
            )
            is TranslationSessionKey.GroupChat -> groupSettings.upsert(
                GroupChatTranslationSetting(session.sessionId, setting.mode, setting.conversationLanguage, setting.providerId)
            )
        }
    }

    /** The stored rows of [messageIds], by message id. */
    suspend fun translations(session: TranslationSessionKey, messageIds: List<Long>): Map<Long, StoredTranslation> {
        if (messageIds.isEmpty()) return emptyMap()
        val rows = when (session) {
            is TranslationSessionKey.Chat -> chatMessages.getByMessageIds(messageIds).map {
                StoredTranslation(it.messageId, it.displayText, it.displayLanguage, it.conversationLanguage, it.contentHash, it.providerId, it.createTime)
            }
            is TranslationSessionKey.GroupChat -> groupMessages.getByMessageIds(messageIds).map {
                StoredTranslation(it.messageId, it.displayText, it.displayLanguage, it.conversationLanguage, it.contentHash, it.providerId, it.createTime)
            }
        }
        return rows.associateBy { it.messageId }
    }

    suspend fun saveTranslation(session: TranslationSessionKey, row: StoredTranslation) {
        when (session) {
            is TranslationSessionKey.Chat -> chatMessages.upsert(
                ChatMessageTranslation(row.messageId, row.displayText, row.displayLanguage, row.conversationLanguage, row.contentHash, row.providerId, row.createTime)
            )
            is TranslationSessionKey.GroupChat -> groupMessages.upsert(
                GroupChatMessageTranslation(row.messageId, row.displayText, row.displayLanguage, row.conversationLanguage, row.contentHash, row.providerId, row.createTime)
            )
        }
    }
}
