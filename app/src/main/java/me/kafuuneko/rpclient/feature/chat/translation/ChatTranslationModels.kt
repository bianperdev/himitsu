// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.chat.translation

import me.kafuuneko.rpclient.libs.translation.TranslationFailure
import me.kafuuneko.rpclient.libs.translation.model.TranslationMode
import me.kafuuneko.rpclient.model.MessageContentPart

/**
 * The translation of one message as the chat screens render it. The message's own content stays
 * in the conversation language; this is only what the user reads.
 */
data class MessageTranslationUi(
    /** The text in the user's language. */
    val displayText: String,
    /** [displayText] split into text and think blocks, like the original content. */
    val parts: List<MessageContentPart>,
    /** True when the user chose to read the original instead. */
    val showingOriginal: Boolean
) {
    override fun toString(): String = "MessageTranslationUi([REDACTED], showingOriginal=$showingOriginal)"
}

/**
 * Translation node of a chat screen's state, shared by one-to-one and group chats. Its default
 * value is what every chat shows while translation is off: nothing.
 */
data class ChatTranslationUiState(
    /** The master switch is on, so the chat's mode can be chosen in its settings. */
    val available: Boolean = false,
    /** The chat's mode (Auto, Always, Never). */
    val mode: TranslationMode = TranslationMode.Auto,
    /** Messages are translated now: the banner and the marks show. */
    val active: Boolean = false,
    /** BCP 47 tag of the conversation language, or null when not known yet. */
    val conversationLanguage: String? = null,
    /** The banner's toggle: every message shows its original. */
    val showOriginal: Boolean = false,
    /** Why the typed message could not be translated; sending it again sends it as written. */
    val outgoingFailure: TranslationFailure? = null
)
