// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/** The kinds of translation service a [TranslationProvider] can be. Stored by name. */
enum class TranslationProviderType {
    /** A LibreTranslate server; recommended on the user's own server, for privacy. */
    LibreTranslate,

    /** DeepL API (free or pro endpoint). */
    DeepL,

    /** Google Cloud Translation (v2 with an API key). */
    GoogleCloud,

    /** One of the configured AI providers, prompted to translate ([TranslationProvider.llmProviderId]). */
    LlmProvider
}

/** Whether a chat translates. Stored by name. */
enum class TranslationMode {
    /** Translates when the character writes in another language than the user's. */
    Auto,

    /** Always translates, between the stored conversation language and the user's. */
    On,

    /** Never translates this chat. */
    Off
}

/**
 * A translation service configured by the user. Lives in the encrypted main database, so
 * [apiKey] and [customHeadersJson] are protected like AI provider keys (never logged).
 */
@Entity(
    tableName = "translation_providers",
    foreignKeys = [
        ForeignKey(
            entity = LLMProvider::class,
            parentColumns = ["id"],
            childColumns = ["llmProviderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("llmProviderId")]
)
data class TranslationProvider(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: TranslationProviderType,
    /** Endpoint; empty means the service's default (DeepL, Google). */
    val baseUrl: String = "",
    val apiKey: String = "",
    /** The AI provider used when [type] is [TranslationProviderType.LlmProvider]. */
    val llmProviderId: Long? = null,
    val customHeadersJson: String = "",
    val createTime: Long
)

/**
 * The text a user reads for one chat message, in the user's language. The message's own content
 * stays in the conversation language, which is what the model sees.
 */
@Entity(
    tableName = "chat_message_translations",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessage::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TranslationProvider::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("providerId")]
)
data class ChatMessageTranslation(
    @PrimaryKey val messageId: Long,
    /** What the user reads, or for a user message what they typed. */
    val displayText: String,
    /** BCP 47 tag of [displayText], for example "es". */
    val displayLanguage: String,
    /** BCP 47 tag of the message content, for example "en". */
    val conversationLanguage: String,
    /** SHA-256 of the message content this row matches; a different content makes the row stale. */
    val contentHash: String,
    val providerId: Long? = null,
    val createTime: Long
)

/** [ChatMessageTranslation] for group chat messages. */
@Entity(
    tableName = "group_chat_message_translations",
    foreignKeys = [
        ForeignKey(
            entity = GroupChatMessage::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TranslationProvider::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("providerId")]
)
data class GroupChatMessageTranslation(
    @PrimaryKey val messageId: Long,
    val displayText: String,
    val displayLanguage: String,
    val conversationLanguage: String,
    val contentHash: String,
    val providerId: Long? = null,
    val createTime: Long
)

/** Translation settings of one chat; a chat without a row uses [TranslationMode.Auto]. */
@Entity(
    tableName = "chat_translation_settings",
    foreignKeys = [
        ForeignKey(
            entity = ChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TranslationProvider::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("providerId")]
)
data class ChatTranslationSetting(
    @PrimaryKey val sessionId: Long,
    val mode: TranslationMode = TranslationMode.Auto,
    /** BCP 47 tag of the conversation, once known; null until detected. */
    val conversationLanguage: String? = null,
    /** Overrides the default provider; null uses the default. */
    val providerId: Long? = null
)

/** [ChatTranslationSetting] for group chats. */
@Entity(
    tableName = "group_chat_translation_settings",
    foreignKeys = [
        ForeignKey(
            entity = GroupChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TranslationProvider::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("providerId")]
)
data class GroupChatTranslationSetting(
    @PrimaryKey val sessionId: Long,
    val mode: TranslationMode = TranslationMode.Auto,
    val conversationLanguage: String? = null,
    val providerId: Long? = null
)
