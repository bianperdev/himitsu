// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.translationsettings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.translation.model.TranslationProviderType

/** Name of a provider type: the service's own name, or "AI provider". */
@Composable
internal fun TranslationProviderType.label(): String = stringResource(
    when (this) {
        TranslationProviderType.LibreTranslate -> R.string.translation_provider_type_libretranslate
        TranslationProviderType.DeepL -> R.string.translation_provider_type_deepl
        TranslationProviderType.GoogleCloud -> R.string.translation_provider_type_google
        TranslationProviderType.LlmProvider -> R.string.translation_provider_type_llm
    }
)

/** Generic icons, not the services' logos. */
internal val TranslationProviderType.icon: ImageVector
    get() = when (this) {
        TranslationProviderType.LibreTranslate -> Icons.Rounded.Dns
        TranslationProviderType.DeepL -> Icons.Rounded.Translate
        TranslationProviderType.GoogleCloud -> Icons.Rounded.Public
        TranslationProviderType.LlmProvider -> Icons.Rounded.Psychology
    }

/** One option of a single-choice list in a dialog. */
@Composable
internal fun TranslationChoiceRow(title: String, selected: Boolean, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
