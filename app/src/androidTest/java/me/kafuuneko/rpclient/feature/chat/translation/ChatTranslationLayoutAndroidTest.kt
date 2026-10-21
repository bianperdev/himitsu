// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.chat.translation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.model.ChatCharacterItem
import me.kafuuneko.rpclient.feature.chat.model.ChatMessageUiModel
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem
import me.kafuuneko.rpclient.feature.chat.model.MessageRole
import me.kafuuneko.rpclient.feature.chat.presentation.ChatConversationState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLorebookState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatPage
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.feature.chat.ui.ChatLayout
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMessageItem
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatConversationState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatSettingsState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiIntent
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiState
import me.kafuuneko.rpclient.feature.groupchat.ui.GroupChatLayout
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatMessageSource
import me.kafuuneko.rpclient.libs.translation.TranslationFailure
import me.kafuuneko.rpclient.libs.translation.model.TranslationMode
import me.kafuuneko.rpclient.model.MessageContentPart
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.DefaultCharacterAccentColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The chat screens render the translation pieces from their state: nothing at all while
 * translation is off, the banner, translated text and marks while it is on.
 */
@RunWith(AndroidJUnit4::class)
class ChatTranslationLayoutAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val original = "The rain kept tapping on the windows."
    private val translated = "La lluvia seguía golpeando las ventanas."

    private fun translation(showingOriginal: Boolean = false) = MessageTranslationUi(
        displayText = translated,
        parts = listOf(MessageContentPart.Text(translated)),
        showingOriginal = showingOriginal
    )

    private fun chatState(
        translationState: ChatTranslationUiState = ChatTranslationUiState(),
        translation: MessageTranslationUi? = null,
        page: ChatPage = ChatPage.Conversation
    ) = ChatUiState.Normal(
        page = page,
        session = ChatSessionItem(
            id = 1, title = "Night", summarize = "", userNote = "", userName = "You", userDescription = "",
            creatorNotes = "", autoSummaryPaused = false, messageCount = 1, enabledLorebookEntryIds = emptySet()
        ),
        character = ChatCharacterItem(
            id = 1, name = "Lyra", description = "", personality = "", scenario = "", examplesOfDialogue = "",
            postHistoryInstructions = "", creatorNotes = "", avatarText = "L", accentColor = DefaultCharacterAccentColor
        ),
        conversationState = ChatConversationState(
            messages = listOf(
                ChatMessageUiModel(
                    id = "7", role = MessageRole.Assistant, speaker = "Lyra", content = original,
                    parts = listOf(MessageContentPart.Text(original)), time = "02:15", tokenCount = 12,
                    translation = translation
                )
            ),
            hasAssistantMessage = true
        ),
        lorebookState = ChatLorebookState(groups = emptyList()),
        streamEnabled = true,
        translationState = translationState
    )

    private val activeState = ChatTranslationUiState(available = true, active = true, conversationLanguage = "en")

    private fun englishName(): String {
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        return Locale.forLanguageTag("en").getDisplayLanguage(locale)
    }

    @Test
    fun translationOffLooksExactlyAsBefore() {
        composeRule.setContent { AppTheme(dynamicColor = false) { ChatLayout(chatState()) {} } }

        composeRule.onNodeWithText(original, substring = true).assertExists()
        composeRule.onNodeWithTag(ChatTranslationTags.BANNER).assertDoesNotExist()
        composeRule.onNodeWithTag(ChatTranslationTags.MARK).assertDoesNotExist()
        composeRule.onNodeWithTag(ChatTranslationTags.OUTGOING_NOTICE).assertDoesNotExist()
    }

    @Test
    fun activeChatShowsTheTranslationTheBannerAndTheMark() {
        val intents = mutableListOf<ChatUiIntent>()
        composeRule.setContent {
            AppTheme(dynamicColor = false) { ChatLayout(chatState(activeState, translation())) { intents += this } }
        }

        composeRule.onNodeWithText(translated, substring = true).assertExists()
        composeRule.onNodeWithText(original, substring = true).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.translation_chat_banner_from, englishName())).assertExists()

        composeRule.onNodeWithTag(ChatTranslationTags.MARK).performClick()
        composeRule.onNodeWithTag(ChatTranslationTags.BANNER_TOGGLE).performClick()
        assertEquals(listOf(ChatUiIntent.ToggleMessageOriginal("7"), ChatUiIntent.ToggleShowOriginal), intents)
    }

    @Test
    fun aToggledMessageShowsItsOriginal() {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                ChatLayout(chatState(activeState.copy(showOriginal = true), translation(showingOriginal = true))) {}
            }
        }

        composeRule.onNodeWithText(original, substring = true).assertExists()
        composeRule.onNodeWithText(translated, substring = true).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.translation_chat_show_translation)).assertExists()
    }

    @Test
    fun aFailedOutgoingTranslationShowsTheQuietNotice() {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                ChatLayout(chatState(activeState.copy(outgoingFailure = TranslationFailure.Network))) {}
            }
        }

        composeRule.onNodeWithTag(ChatTranslationTags.OUTGOING_NOTICE).assertExists()
    }

    @Test
    fun chatSettingsOfferTheModeOnlyWhileTranslationIsAvailable() {
        val intents = mutableListOf<ChatUiIntent>()
        val state = mutableStateOf(chatState(page = ChatPage.Settings))
        composeRule.setContent { AppTheme(dynamicColor = false) { ChatLayout(state.value) { intents += this } } }
        val always = context.getString(R.string.translation_chat_mode_on)

        composeRule.onNodeWithText(always).assertDoesNotExist()

        state.value = chatState(activeState, page = ChatPage.Settings)
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(always))
        composeRule.onNodeWithText(always).performClick()
        assertTrue(ChatUiIntent.SelectTranslationMode(TranslationMode.On) in intents)
    }

    @Test
    fun groupChatShowsTheTranslationAndTogglesById() {
        val intents = mutableListOf<GroupChatUiIntent>()
        val state = GroupChatUiState.Normal(
            sessionId = 1,
            title = "Crew",
            members = emptyList(),
            activeActivationStrategy = GroupChatActivationStrategy.Natural,
            conversationState = GroupChatConversationState(
                messages = listOf(
                    GroupChatMessageItem(
                        id = 9, source = GroupChatMessageSource.Character, speakerName = "Mina",
                        content = original, time = "21:04", translation = translation()
                    )
                ),
                hasCharacterMessage = true,
                selectedSpeakerId = null
            ),
            settingsState = GroupChatSettingsState(activationStrategy = GroupChatActivationStrategy.Natural),
            translationState = activeState
        )
        composeRule.setContent { AppTheme(dynamicColor = false) { GroupChatLayout(state) { intents += it } } }

        composeRule.onNodeWithText(translated, substring = true).assertExists()
        composeRule.onNodeWithTag(ChatTranslationTags.BANNER).assertExists()
        composeRule.onNodeWithTag(ChatTranslationTags.MARK).performClick()
        assertEquals(listOf<GroupChatUiIntent>(GroupChatUiIntent.ToggleMessageOriginal(9)), intents)
    }
}
