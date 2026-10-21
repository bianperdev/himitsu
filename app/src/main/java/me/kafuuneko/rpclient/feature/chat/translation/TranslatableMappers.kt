// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.chat.translation

import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.translation.TranslatableMessage

/**
 * Stored chat messages as the translator sees them: their content as stored (the conversation
 * language), without summaries, blank placeholders or the message being streamed.
 *
 * @param names the character's and the user's names, protected from translation.
 * @param streamingId the message the model is writing, left out until it is stored.
 */
fun List<ChatMessage>.toTranslatableMessages(names: List<String>, streamingId: Long?): List<TranslatableMessage> =
    mapNotNull { message ->
        if (message.id == streamingId || message.content.isBlank()) return@mapNotNull null
        val fromUser = when (message.source) {
            ChatMessage.Source.User -> true
            ChatMessage.Source.Char, ChatMessage.Source.System -> false
            ChatMessage.Source.Summary -> return@mapNotNull null
        }
        TranslatableMessage(id = message.id, content = message.content, fromUser = fromUser, names = names)
    }

/** Group chat messages as the translator sees them; see the one-to-one variant above. */
fun List<GroupChatMessage>.toGroupTranslatableMessages(names: List<String>, streamingId: Long?): List<TranslatableMessage> =
    mapNotNull { message ->
        if (message.id == streamingId || message.content.isBlank()) return@mapNotNull null
        TranslatableMessage(
            id = message.id,
            content = message.content,
            fromUser = message.source == GroupChatMessage.Source.User,
            names = names
        )
    }
