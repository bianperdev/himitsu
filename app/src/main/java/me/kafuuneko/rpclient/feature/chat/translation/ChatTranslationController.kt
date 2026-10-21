// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.chat.translation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.translation.ChatTranslator
import me.kafuuneko.rpclient.libs.translation.TranslatableMessage
import me.kafuuneko.rpclient.libs.translation.TranslationException
import me.kafuuneko.rpclient.libs.translation.TranslationFailure
import me.kafuuneko.rpclient.libs.translation.TranslationSessionKey
import me.kafuuneko.rpclient.libs.translation.TranslationState
import me.kafuuneko.rpclient.libs.translation.model.TranslationMode
import me.kafuuneko.rpclient.model.toMessageContentParts

/**
 * Translation glue shared by the chat and group chat ViewModels, so both screens behave alike
 * and their own changes stay small.
 *
 * - Keeps a snapshot of the loaded messages (their content in the conversation language) and
 *   asks [ChatTranslator] for display texts in the background, off the intent queue, so a slow
 *   translation service never delays loading, sending or streaming.
 * - Resolves the chat's state when it opens, when it resumes and when a character message
 *   completes or changes; the streaming message is left out by the caller until it is stored.
 * - Holds which messages show their original, the banner toggle and the last outgoing failure.
 * - Does nothing at all while the master switch ([isEnabled]) is off: no translator call, no job.
 *
 * Every method may be called from any thread; [onChanged] runs on [scope]'s dispatcher. Nothing
 * here logs or keeps text beyond the screen's lifetime.
 */
class ChatTranslationController(
    private val translator: ChatTranslator,
    private val scope: CoroutineScope,
    private val isEnabled: () -> Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onChanged: () -> Unit
) {
    /** Result of preparing what the user typed for sending or saving. */
    sealed class Outgoing {
        /** Store [content]; remember [displayText] for the new message when it is not null. */
        data class Ready(val content: String, val displayText: String?) : Outgoing() {
            override fun toString(): String = "Ready([REDACTED], translated=${displayText != null})"
        }

        /** Translation failed; the same text sent again goes as written. */
        data class Failed(val failure: TranslationFailure) : Outgoing()
    }

    /** What the user typed or edited: a new message, or an edit of a stored one. */
    enum class Purpose { Send, Edit }

    /** A display text and the content it was made from; a content change makes it stale. */
    private data class TranslatedText(val content: String, val displayText: String)

    private val mLock = Any()
    private var mSession: TranslationSessionKey? = null
    private var mState: TranslationState? = null
    /** Loaded messages by id, oldest first, as stored (not streaming, not blank). */
    private var mWindow: Map<Long, TranslatableMessage> = emptyMap()
    private var mTexts: Map<Long, TranslatedText> = emptyMap()
    private var mShowAllOriginal = false
    /** Messages whose original/translation choice differs from the banner's. */
    private var mToggledIds: Set<Long> = emptySet()
    private var mFailedInputs: Map<Purpose, String> = emptyMap()
    private var mOutgoingFailure: TranslationFailure? = null
    private var mSyncRunning = false
    private var mSyncPending = false
    private var mResolvePending = false

    /** Binds the controller to its chat; the other calls are ignored before this. */
    fun attach(session: TranslationSessionKey) {
        synchronized(mLock) { mSession = session }
    }

    /**
     * Records the messages the screen just loaded and translates what is missing or stale.
     *
     * @param messages the stored messages of the window, oldest first, without the streaming one.
     * @param older true for a page of older messages added on top of the current window.
     */
    fun onMessagesLoaded(messages: List<TranslatableMessage>, older: Boolean = false) {
        if (!isEnabled()) {
            clearIfDisabled()
            return
        }
        // Compare with the previous snapshot: only a real change starts work.
        val (changed, incomingChanged) = synchronized(mLock) {
            val previous = mWindow
            val next = LinkedHashMap<Long, TranslatableMessage>()
            if (older) {
                messages.forEach { next[it.id] = it }
                next.putAll(previous)
            } else {
                messages.forEach { next[it.id] = it }
            }
            mWindow = next
            val incoming = !older && messages.any { !it.fromUser && previous[it.id]?.content != it.content }
            (next != previous || mState == null) to incoming
        }
        if (changed) requestSync(resolve = incomingChanged)
    }

    /** Resolves again when the screen comes back: the settings may have changed meanwhile. */
    fun onResume() {
        if (!isEnabled()) {
            clearIfDisabled()
            return
        }
        requestSync(resolve = true)
    }

    /**
     * The translation to render for message [id], or null to render the message as is.
     *
     * @param isStreaming true while the model writes it: the original always shows then.
     */
    fun translationFor(id: Long, isStreaming: Boolean = false): MessageTranslationUi? {
        if (isStreaming) return null
        synchronized(mLock) {
            if (mState?.active != true) return null
            val entry = mTexts[id] ?: return null
            // A translation made from older content is stale: show the original until it is redone.
            if (mWindow[id]?.content != entry.content) return null
            return MessageTranslationUi(
                displayText = entry.displayText,
                parts = entry.displayText.toMessageContentParts(id.toString()),
                showingOriginal = mShowAllOriginal != (id in mToggledIds)
            )
        }
    }

    /** The screen's translation node; the default (nothing shown) while translation is off. */
    fun uiState(): ChatTranslationUiState {
        if (!isEnabled()) return ChatTranslationUiState()
        synchronized(mLock) {
            val state = mState
            return ChatTranslationUiState(
                available = mSession != null,
                mode = state?.mode ?: TranslationMode.Auto,
                active = state?.active == true,
                conversationLanguage = state?.conversationLanguage,
                showOriginal = mShowAllOriginal,
                outgoingFailure = mOutgoingFailure
            )
        }
    }

    /** Switches message [id] between its translation and its original. */
    fun toggleOriginal(id: Long) {
        synchronized(mLock) {
            mToggledIds = if (id in mToggledIds) mToggledIds - id else mToggledIds + id
        }
    }

    /** The banner's toggle: every message shows its original, or every one its translation. */
    fun toggleShowAllOriginal() {
        synchronized(mLock) {
            mShowAllOriginal = !mShowAllOriginal
            mToggledIds = emptySet()
        }
    }

    /**
     * The display text to put in the editor of a user message, or null to edit its content.
     * Only a user message whose translation is shown is edited in the user's language.
     */
    fun editableDisplayText(id: Long, content: String): String? {
        synchronized(mLock) {
            if (mState?.active != true) return null
            if (mWindow[id]?.fromUser != true) return null
            val entry = mTexts[id]?.takeIf { it.content == content } ?: return null
            if (mShowAllOriginal != (id in mToggledIds)) return null
            return entry.displayText
        }
    }

    /** True when sending [input] now would go through the translator. */
    fun translatesOutgoing(input: String, purpose: Purpose = Purpose.Send): Boolean {
        if (!isEnabled() || input.isBlank() || input.startsWith(COMMAND_PREFIX)) return false
        synchronized(mLock) {
            return mSession != null && mState?.active == true && mFailedInputs[purpose] != input
        }
    }

    /**
     * Turns what the user typed into the conversation-language text to store.
     *
     * - Not translating (off, inactive, blank, or a slash command): the input as is.
     * - On failure the input is kept as the failed one and [Outgoing.Failed] returned; preparing
     *   the same input again returns it untranslated, so a failure never blocks sending.
     */
    suspend fun prepareOutgoing(input: String, names: List<String>, purpose: Purpose = Purpose.Send): Outgoing {
        val session = synchronized(mLock) { mSession }
        if (session == null || !translatesOutgoing(input, purpose)) {
            synchronized(mLock) {
                if (mFailedInputs[purpose] == input) mFailedInputs = mFailedInputs - purpose
                if (purpose == Purpose.Send) mOutgoingFailure = null
            }
            return Outgoing.Ready(content = input, displayText = null)
        }
        // Call the translator off the main thread; an unexpected error counts as a failure.
        var failure: TranslationFailure? = null
        val translated = try {
            withContext(ioDispatcher) { translator.outgoing(session, input, names) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failure = (error as? TranslationException)?.failure ?: TranslationFailure.Network
            null
        }
        if (failure == null) failure = translated?.failure
        synchronized(mLock) {
            if (failure != null) {
                mFailedInputs = mFailedInputs + (purpose to input)
                if (purpose == Purpose.Send) mOutgoingFailure = failure
            } else {
                mFailedInputs = mFailedInputs - purpose
                if (purpose == Purpose.Send) mOutgoingFailure = null
            }
        }
        if (failure != null || translated == null) return Outgoing.Failed(failure ?: TranslationFailure.Invalid)
        return Outgoing.Ready(content = translated.content, displayText = translated.displayText)
    }

    /**
     * Forgets the outgoing failure once the draft no longer matches the text that failed.
     *
     * @return true when the screen's translation node changed.
     */
    fun onDraftChanged(draft: String): Boolean {
        synchronized(mLock) {
            val failed = mFailedInputs[Purpose.Send] ?: return false
            if (failed == draft.trim()) return false
            mFailedInputs = mFailedInputs - Purpose.Send
            val hadNotice = mOutgoingFailure != null
            mOutgoingFailure = null
            return hadNotice
        }
    }

    /**
     * Stores what the user typed for the message saved with [content], and shows it at once.
     * Runs to completion even if the sending job is cancelled right after the message was saved.
     */
    suspend fun rememberOutgoing(messageId: Long, content: String, displayText: String) {
        val session = synchronized(mLock) {
            mTexts = mTexts + (messageId to TranslatedText(content, displayText))
            mSession
        } ?: return
        withContext(NonCancellable + ioDispatcher) {
            try {
                translator.rememberOutgoing(session, messageId, content, displayText)
            } catch (error: Exception) {
                // Keep the local copy: the next display sync asks the core again.
            }
        }
    }

    /** Changes the chat's mode and resolves again. */
    suspend fun setMode(mode: TranslationMode) {
        val session = synchronized(mLock) { mSession } ?: return
        if (!isEnabled()) return
        try {
            withContext(ioDispatcher) { translator.setMode(session, mode) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return
        }
        synchronized(mLock) { mState = mState?.copy(mode = mode) }
        requestSync(resolve = true)
    }

    /** Drops every translation when the master switch was turned off. */
    private fun clearIfDisabled() {
        val hadState = synchronized(mLock) {
            val had = mState != null || mTexts.isNotEmpty()
            mState = null
            mTexts = emptyMap()
            mWindow = emptyMap()
            mOutgoingFailure = null
            mFailedInputs = emptyMap()
            had
        }
        if (hadState) scope.launch { onChanged() }
    }

    /** Queues a sync; requests arriving while one runs are merged into a single next run. */
    private fun requestSync(resolve: Boolean) {
        val start = synchronized(mLock) {
            if (mSession == null) return
            mSyncPending = true
            mResolvePending = mResolvePending || resolve
            if (mSyncRunning) {
                false
            } else {
                mSyncRunning = true
                true
            }
        }
        if (start) scope.launch { drainSyncs() }
    }

    /** Runs queued syncs one after another until none is left. */
    private suspend fun drainSyncs() {
        try {
            while (true) {
                val resolve = synchronized(mLock) {
                    if (!mSyncPending) {
                        mSyncRunning = false
                        return
                    }
                    mSyncPending = false
                    val resolve = mResolvePending || mState == null
                    mResolvePending = false
                    resolve
                }
                sync(resolve)
            }
        } catch (error: Throwable) {
            synchronized(mLock) { mSyncRunning = false }
            throw error
        }
    }

    /**
     * One sync: resolve when asked, then fetch display texts for the loaded messages that have
     * none or a stale one. A failure leaves those messages showing their original.
     */
    private suspend fun sync(resolve: Boolean) {
        val session = synchronized(mLock) { mSession } ?: return
        if (!isEnabled()) {
            clearIfDisabled()
            return
        }
        val window = synchronized(mLock) { mWindow.values.toList() }
        // Decide whether this chat translates, from its latest messages.
        if (resolve) {
            val resolved = try {
                withContext(ioDispatcher) { translator.resolve(session, window.takeLast(RECENT_MESSAGES)) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                null
            }
            if (resolved != null) synchronized(mLock) { mState = resolved }
        }
        if (synchronized(mLock) { mState?.active } != true) {
            synchronized(mLock) { mTexts = emptyMap() }
            onChanged()
            return
        }
        // Ask only for the messages without an up-to-date display text.
        val missing = synchronized(mLock) {
            window.filter { mTexts[it.id]?.content != it.content }
        }
        if (missing.isEmpty()) {
            onChanged()
            return
        }
        val texts = try {
            withContext(ioDispatcher) { translator.displayTexts(session, missing) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            emptyMap()
        }
        synchronized(mLock) {
            val updated = mTexts.toMutableMap()
            missing.forEach { message ->
                val text = texts[message.id]
                if (text == null) updated.remove(message.id)
                else updated[message.id] = TranslatedText(message.content, text)
            }
            mTexts = updated
        }
        onChanged()
    }

    private companion object {
        /** How many of the latest messages [ChatTranslator.resolve] looks at. */
        const val RECENT_MESSAGES = 10

        /** Slash commands are instructions to the app, not chat text: never translated. */
        const val COMMAND_PREFIX = "/"
    }
}
