// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.chat.translation

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.translation.TranslationFailure
import me.kafuuneko.rpclient.libs.translation.model.TranslationMode
import me.kafuuneko.rpclient.ui.theme.AppTheme
import java.util.Locale

/** Test tags of the translation pieces, used by the instrumented tests. */
object ChatTranslationTags {
    const val BANNER = "translation_banner"
    const val BANNER_TOGGLE = "translation_banner_toggle"
    const val MARK = "translation_mark"
    const val OUTGOING_NOTICE = "translation_outgoing_notice"
}

/**
 * The slim banner over the messages while the chat is translated: "Translating from English ·
 * Show original". Renders nothing while translation is inactive.
 */
@Composable
fun ChatTranslationBanner(
    state: ChatTranslationUiState,
    onToggleOriginal: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.active) return
    val languageName = languageDisplayName(state.conversationLanguage)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ChatTranslationTags.BANNER),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Translate,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (languageName != null) {
                    stringResource(R.string.translation_chat_banner_from, languageName)
                } else {
                    stringResource(R.string.translation_chat_banner_generic)
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onToggleOriginal,
                modifier = Modifier.testTag(ChatTranslationTags.BANNER_TOGGLE)
            ) {
                Text(
                    text = stringResource(
                        if (state.showOriginal) R.string.translation_chat_show_translation
                        else R.string.translation_chat_show_original
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * The small mark under a translated message: "Translated · Show original", or "Original · Show
 * translation" once toggled. Tapping it switches the message.
 */
@Composable
fun MessageTranslationMark(
    translation: MessageTranslationUi,
    color: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(
        if (translation.showingOriginal) R.string.translation_chat_mark_original
        else R.string.translation_chat_mark_translated
    )
    val action = stringResource(
        if (translation.showingOriginal) R.string.translation_chat_show_translation
        else R.string.translation_chat_show_original
    )
    Row(
        modifier = modifier
            .heightIn(min = 32.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .testTag(ChatTranslationTags.MARK),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Translate,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = color
        )
        Text(
            text = "$label · $action",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The quiet notice over the composer when the typed message could not be translated. Sending
 * again sends it as written. Renders nothing without a failure.
 */
@Composable
fun OutgoingTranslationNotice(
    failure: TranslationFailure?,
    modifier: Modifier = Modifier
) {
    if (failure == null) return
    Text(
        text = stringResource(R.string.translation_chat_send_failed, stringResource(failure.reasonResId())),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag(ChatTranslationTags.OUTGOING_NOTICE)
    )
}

/**
 * The per-chat translation choice for the chat settings: Auto, Always or Never. Renders nothing
 * while the master switch is off, since the choice would have no effect.
 */
@Composable
fun ChatTranslationModeSelector(
    state: ChatTranslationUiState,
    onModeSelected: (TranslationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.available) return
    Column(modifier = modifier.selectableGroup()) {
        TranslationMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = mode == state.mode,
                        role = Role.RadioButton,
                        onClick = { onModeSelected(mode) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = mode == state.mode, onClick = null)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(text = stringResource(mode.titleResId()), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(mode.descriptionResId()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
                    )
                }
            }
        }
    }
}

/** The language of [tag] named in the app's language, or null when unknown. */
@Composable
private fun languageDisplayName(tag: String?): String? {
    val configuration = LocalConfiguration.current
    return remember(tag, configuration) {
        if (tag.isNullOrBlank()) return@remember null
        val appLocale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
        Locale.forLanguageTag(tag).getDisplayLanguage(appLocale).takeIf { it.isNotBlank() && it != tag }
    }
}

/** The short reason shown for a translation failure. */
@StringRes
fun TranslationFailure.reasonResId(): Int = when (this) {
    TranslationFailure.Network -> R.string.translation_chat_failure_network
    TranslationFailure.Auth -> R.string.translation_chat_failure_auth
    TranslationFailure.Quota -> R.string.translation_chat_failure_quota
    TranslationFailure.Unsupported -> R.string.translation_chat_failure_unsupported
    TranslationFailure.Invalid -> R.string.translation_chat_failure_invalid
}

@StringRes
private fun TranslationMode.titleResId(): Int = when (this) {
    TranslationMode.Auto -> R.string.translation_chat_mode_auto
    TranslationMode.On -> R.string.translation_chat_mode_on
    TranslationMode.Off -> R.string.translation_chat_mode_off
}

@StringRes
private fun TranslationMode.descriptionResId(): Int = when (this) {
    TranslationMode.Auto -> R.string.translation_chat_mode_auto_desc
    TranslationMode.On -> R.string.translation_chat_mode_on_desc
    TranslationMode.Off -> R.string.translation_chat_mode_off_desc
}

@Preview(showBackground = true)
@Composable
private fun ChatTranslationWidgetsPreview() {
    AppTheme(dynamicColor = false) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val state = ChatTranslationUiState(available = true, active = true, conversationLanguage = "en")
            ChatTranslationBanner(state = state, onToggleOriginal = {})
            MessageTranslationMark(
                translation = MessageTranslationUi(displayText = "Hola", parts = emptyList(), showingOriginal = false),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                onToggle = {},
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            OutgoingTranslationNotice(failure = TranslationFailure.Network)
            ChatTranslationModeSelector(state = state, onModeSelected = {}, modifier = Modifier.padding(16.dp))
        }
    }
}
