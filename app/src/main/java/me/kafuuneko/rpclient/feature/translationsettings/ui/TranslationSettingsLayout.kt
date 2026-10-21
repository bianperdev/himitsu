// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.translationsettings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.translationsettings.model.TranslationLanguageOption
import me.kafuuneko.rpclient.feature.translationsettings.model.TranslationProviderListItem
import me.kafuuneko.rpclient.feature.translationsettings.presentation.TranslationSettingsDialogState
import me.kafuuneko.rpclient.feature.translationsettings.presentation.TranslationSettingsUiIntent
import me.kafuuneko.rpclient.feature.translationsettings.presentation.TranslationSettingsUiState
import me.kafuuneko.rpclient.libs.translation.model.TranslationProviderType
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.HimitsuPhonePreviews
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsSwitchTile
import me.kafuuneko.rpclient.ui.widgets.RpSettingsTile
import me.kafuuneko.rpclient.ui.widgets.RpSettingsValueTile

/** Entry of the Translation settings screen. */
@Composable
fun TranslationSettingsLayout(uiState: TranslationSettingsUiState, emit: TranslationSettingsUiIntent.() -> Unit) {
    BackHandler(enabled = uiState is TranslationSettingsUiState.Normal) { TranslationSettingsUiIntent.Back.emit() }
    when (uiState) {
        TranslationSettingsUiState.None -> Unit
        is TranslationSettingsUiState.Normal -> {
            NormalView(uiState, emit)
            SettingsDialog(uiState, emit)
        }
        is TranslationSettingsUiState.Finished -> TranslationSettingsLayout(uiState.previous) { }
    }
}

@Composable
private fun NormalView(uiState: TranslationSettingsUiState.Normal, emit: TranslationSettingsUiIntent.() -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { AppTopBar(title = stringResource(R.string.translation_settings_title), onBack = { TranslationSettingsUiIntent.Back.emit() }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RpInfoCard(
                icon = Icons.Rounded.Translate,
                title = stringResource(R.string.translation_settings_about_title),
                subtitle = stringResource(R.string.translation_settings_about_text)
            )
            GeneralGroup(uiState, emit)
            RpSectionHeader(
                title = stringResource(R.string.translation_providers_section),
                action = stringResource(R.string.translation_providers_add),
                onAction = { TranslationSettingsUiIntent.AddProvider.emit() }
            )
            ProvidersGroup(uiState.providers, uiState.defaultProviderId, emit)
            LibreTranslateNote(emit)
        }
    }
}

@Composable
private fun GeneralGroup(uiState: TranslationSettingsUiState.Normal, emit: TranslationSettingsUiIntent.() -> Unit) {
    RpSettingsGroup {
        RpSettingsSwitchTile(
            title = stringResource(R.string.translation_settings_enabled),
            subtitle = stringResource(
                when {
                    !uiState.enabled -> R.string.translation_settings_enabled_off
                    uiState.providers.isEmpty() -> R.string.translation_settings_no_provider_hint
                    else -> R.string.translation_settings_enabled_on
                }
            ),
            icon = Icons.Rounded.Translate,
            checked = uiState.enabled,
            onCheckedChange = { TranslationSettingsUiIntent.ToggleEnabled(it).emit() }
        )
        RpSettingsDivider()
        RpSettingsValueTile(
            title = stringResource(R.string.translation_settings_language),
            value = if (uiState.userLanguage.tag.isEmpty()) {
                stringResource(R.string.translation_settings_language_app, uiState.appLanguage.name)
            } else {
                uiState.userLanguage.name
            },
            icon = Icons.Rounded.Language,
            onClick = { TranslationSettingsUiIntent.ShowLanguagePicker.emit() }
        )
        RpSettingsDivider()
        RpSettingsValueTile(
            title = stringResource(R.string.translation_settings_default_provider),
            value = uiState.defaultProvider?.name ?: stringResource(R.string.translation_settings_default_provider_none),
            icon = Icons.Rounded.Tune,
            enabled = uiState.providers.isNotEmpty(),
            onClick = { TranslationSettingsUiIntent.ShowDefaultProviderPicker.emit() }
        )
        RpSettingsDivider()
        RpSettingsSwitchTile(
            title = stringResource(R.string.translation_settings_own_messages),
            subtitle = stringResource(R.string.translation_settings_own_messages_desc),
            icon = Icons.Rounded.RecordVoiceOver,
            checked = uiState.translateOwnMessages,
            onCheckedChange = { TranslationSettingsUiIntent.ToggleTranslateOwnMessages(it).emit() }
        )
    }
}

@Composable
private fun ProvidersGroup(
    providers: List<TranslationProviderListItem>,
    defaultProviderId: Long?,
    emit: TranslationSettingsUiIntent.() -> Unit
) {
    RpSettingsGroup {
        if (providers.isEmpty()) {
            RpSettingsTile(
                title = stringResource(R.string.translation_providers_empty),
                subtitle = stringResource(R.string.translation_providers_empty_desc),
                icon = Icons.Rounded.Add,
                onClick = { TranslationSettingsUiIntent.AddProvider.emit() }
            )
        } else {
            providers.forEachIndexed { index, provider ->
                if (index > 0) RpSettingsDivider()
                ProviderTile(provider, isDefault = provider.id == defaultProviderId, emit = emit)
            }
        }
    }
}

@Composable
private fun ProviderTile(provider: TranslationProviderListItem, isDefault: Boolean, emit: TranslationSettingsUiIntent.() -> Unit) {
    val typeLabel = provider.type.label()
    RpSettingsTile(
        title = provider.name,
        subtitle = if (provider.endpoint.isBlank()) typeLabel else "$typeLabel · ${provider.endpoint}",
        icon = provider.type.icon,
        iconColor = if (provider.unencrypted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        onClick = { TranslationSettingsUiIntent.EditProvider(provider.id).emit() },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isDefault) RpMetaPill(stringResource(R.string.translation_providers_default_badge))
                if (provider.unencrypted) RpMetaPill(stringResource(R.string.translation_providers_unencrypted_badge))
                IconButton(onClick = { TranslationSettingsUiIntent.ShowDeleteProviderDialog(provider.id).emit() }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    )
}

/** The recommendation of a self-hosted LibreTranslate, with a link to its project page. */
@Composable
private fun LibreTranslateNote(emit: TranslationSettingsUiIntent.() -> Unit) {
    RpInfoCard(
        icon = Icons.Rounded.Shield,
        title = stringResource(R.string.translation_providers_libre_title),
        subtitle = stringResource(R.string.translation_providers_libre_note),
        trailing = {
            IconButton(onClick = { TranslationSettingsUiIntent.OpenLibreTranslate.emit() }) {
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = stringResource(R.string.translation_providers_libre_link),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}

@Composable
private fun SettingsDialog(uiState: TranslationSettingsUiState.Normal, emit: TranslationSettingsUiIntent.() -> Unit) {
    val dismiss = { TranslationSettingsUiIntent.DismissDialog.emit() }
    when (val dialog = uiState.dialogState) {
        TranslationSettingsDialogState.None -> Unit
        is TranslationSettingsDialogState.DataNotice -> AppDialogScaffold(
            onDismissRequest = dismiss,
            title = stringResource(R.string.translation_data_notice_title),
            subtitle = stringResource(R.string.translation_data_notice_text),
            badgeIcon = Icons.Rounded.Lock,
            confirmText = stringResource(R.string.translation_data_notice_accept),
            onConfirm = { TranslationSettingsUiIntent.AcceptDataNotice.emit() }
        )
        TranslationSettingsDialogState.LanguagePicker -> AppDialogScaffold(
            onDismissRequest = dismiss,
            title = stringResource(R.string.translation_settings_language),
            subtitle = stringResource(R.string.translation_settings_language_desc),
            badgeIcon = Icons.Rounded.Language,
            compactHeader = true,
            confirmText = "",
            onConfirm = null,
            scrollableContent = true
        ) {
            TranslationChoiceRow(
                title = stringResource(R.string.translation_settings_language_app, uiState.appLanguage.name),
                selected = uiState.userLanguage.tag.isEmpty(),
                onClick = { TranslationSettingsUiIntent.SelectLanguage("").emit() }
            )
            uiState.languages.forEach { language ->
                TranslationChoiceRow(
                    title = language.name,
                    selected = uiState.userLanguage.tag == language.tag,
                    onClick = { TranslationSettingsUiIntent.SelectLanguage(language.tag).emit() }
                )
            }
        }
        TranslationSettingsDialogState.DefaultProviderPicker -> AppDialogScaffold(
            onDismissRequest = dismiss,
            title = stringResource(R.string.translation_settings_default_provider),
            badgeIcon = Icons.Rounded.Tune,
            compactHeader = true,
            confirmText = "",
            onConfirm = null,
            scrollableContent = true
        ) {
            uiState.providers.forEach { provider ->
                TranslationChoiceRow(
                    title = provider.name,
                    subtitle = provider.type.label(),
                    selected = uiState.defaultProviderId == provider.id,
                    onClick = { TranslationSettingsUiIntent.SelectDefaultProvider(provider.id).emit() }
                )
            }
            TranslationChoiceRow(
                title = stringResource(R.string.translation_settings_default_provider_none),
                selected = uiState.defaultProviderId == null,
                onClick = { TranslationSettingsUiIntent.SelectDefaultProvider(null).emit() }
            )
        }
        is TranslationSettingsDialogState.DeleteProvider -> AppDangerDialog(
            onDismissRequest = dismiss,
            title = stringResource(R.string.translation_providers_delete_title),
            message = stringResource(R.string.translation_providers_delete_message, dialog.providerName),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            confirmEnabled = !dialog.isDeleting,
            isConfirmLoading = dialog.isDeleting,
            onConfirm = { TranslationSettingsUiIntent.ConfirmDeleteProvider.emit() }
        )
    }
}

@HimitsuPhonePreviews
@Composable
private fun TranslationSettingsLayoutPreview() {
    AppTheme(dynamicColor = false) {
        TranslationSettingsLayout(
            uiState = TranslationSettingsUiState.Normal(
                enabled = true,
                userLanguage = TranslationLanguageOption.of("es"),
                appLanguage = TranslationLanguageOption.of("es"),
                languages = TranslationLanguageOption.TAGS.map(TranslationLanguageOption::of),
                translateOwnMessages = true,
                providers = listOf(
                    TranslationProviderListItem(1, "Home server", TranslationProviderType.LibreTranslate, "192.168.1.20", unencrypted = true),
                    TranslationProviderListItem(2, "DeepL", TranslationProviderType.DeepL, "", unencrypted = false),
                    TranslationProviderListItem(3, "On-device model", TranslationProviderType.LlmProvider, "Gemma", unencrypted = false)
                ),
                defaultProviderId = 1
            ),
            emit = {}
        )
    }
}
