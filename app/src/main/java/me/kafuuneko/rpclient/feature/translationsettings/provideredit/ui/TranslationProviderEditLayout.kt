// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.translationsettings.provideredit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.llmprovideredit.model.CredentialEditMode
import me.kafuuneko.rpclient.feature.translationsettings.provideredit.presentation.LlmProviderOption
import me.kafuuneko.rpclient.feature.translationsettings.provideredit.presentation.TranslationProviderEditDialogState
import me.kafuuneko.rpclient.feature.translationsettings.provideredit.presentation.TranslationProviderEditForm
import me.kafuuneko.rpclient.feature.translationsettings.provideredit.presentation.TranslationProviderEditUiIntent
import me.kafuuneko.rpclient.feature.translationsettings.provideredit.presentation.TranslationProviderEditUiState
import me.kafuuneko.rpclient.feature.translationsettings.provideredit.presentation.TranslationProviderTestState
import me.kafuuneko.rpclient.feature.translationsettings.ui.TranslationChoiceRow
import me.kafuuneko.rpclient.feature.translationsettings.ui.icon
import me.kafuuneko.rpclient.feature.translationsettings.ui.label
import me.kafuuneko.rpclient.libs.security.CleartextPolicy
import me.kafuuneko.rpclient.libs.translation.TranslationFailure
import me.kafuuneko.rpclient.libs.translation.model.TranslationProviderType
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.AppInputDialog
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.HimitsuPhonePreviews
import me.kafuuneko.rpclient.ui.theme.himitsuColors
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpPanel
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpSettingsValueTile

/** Entry of the translation service editor. */
@Composable
fun TranslationProviderEditLayout(uiState: TranslationProviderEditUiState, emit: TranslationProviderEditUiIntent.() -> Unit) {
    BackHandler(enabled = uiState is TranslationProviderEditUiState.Normal) { TranslationProviderEditUiIntent.Back.emit() }
    when (uiState) {
        TranslationProviderEditUiState.None -> Unit
        is TranslationProviderEditUiState.Normal -> {
            NormalView(uiState, emit)
            EditDialog(uiState, emit)
        }
        is TranslationProviderEditUiState.Finished -> TranslationProviderEditLayout(uiState.previous) { }
    }
}

@Composable
private fun NormalView(uiState: TranslationProviderEditUiState.Normal, emit: TranslationProviderEditUiIntent.() -> Unit) {
    val form = uiState.form
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(if (uiState.isCreate) R.string.translation_provider_create_title else R.string.translation_provider_edit_title),
            onBack = { TranslationProviderEditUiIntent.Back.emit() },
            actions = { SaveButton(uiState, emit) }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RpPanel {
                RpSectionHeader(title = stringResource(R.string.translation_provider_type))
                TypeChips(form.type, emit)
                Text(
                    text = stringResource(form.type.sendsNoteRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = form.name,
                    onValueChange = { TranslationProviderEditUiIntent.ChangeName(it).emit() },
                    label = { Text(stringResource(R.string.name)) },
                    placeholder = { Text(form.type.label()) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                if (form.usesHttpEndpoint) {
                    EndpointFields(form, emit)
                } else {
                    LlmProviderField(uiState.selectedLlmProvider, uiState.llmProviders.isEmpty(), emit)
                }
            }
            TestPanel(uiState.testState, emit)
        }
    }
}

@Composable
private fun TypeChips(selected: TranslationProviderType, emit: TranslationProviderEditUiIntent.() -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TranslationProviderType.entries.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { TranslationProviderEditUiIntent.SelectType(type).emit() },
                label = { Text(type.label()) },
                leadingIcon = { Icon(type.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

/** Address and key of an HTTP service; the key is set in a masked dialog and never shown. */
@Composable
private fun EndpointFields(form: TranslationProviderEditForm, emit: TranslationProviderEditUiIntent.() -> Unit) {
    val isLibre = form.type == TranslationProviderType.LibreTranslate
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = form.baseUrl,
        onValueChange = { TranslationProviderEditUiIntent.ChangeBaseUrl(it).emit() },
        label = { Text(stringResource(if (isLibre) R.string.translation_provider_base_url_server else R.string.translation_provider_base_url_optional)) },
        placeholder = { Text(stringResource(if (isLibre) R.string.translation_provider_base_url_server_hint else R.string.translation_provider_base_url_default_hint)) },
        supportingText = {
            Text(
                stringResource(
                    when (form.type) {
                        TranslationProviderType.DeepL -> R.string.translation_provider_base_url_deepl_desc
                        TranslationProviderType.GoogleCloud -> R.string.translation_provider_base_url_google_desc
                        else -> R.string.translation_provider_base_url_server_desc
                    }
                )
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        shape = RoundedCornerShape(12.dp)
    )
    if (CleartextPolicy.isUnencryptedRemote(form.baseUrl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.translation_provider_cleartext_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
    ApiKeyControl(form, optional = isLibre, emit = emit)
}

@Composable
private fun ApiKeyControl(form: TranslationProviderEditForm, optional: Boolean, emit: TranslationProviderEditUiIntent.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.api_key), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(
            text = when (form.apiKeyEditMode) {
                CredentialEditMode.KeepExisting -> when {
                    form.hasExistingApiKey -> stringResource(R.string.credential_keep_existing)
                    optional -> stringResource(R.string.translation_provider_api_key_optional)
                    else -> stringResource(R.string.credential_not_set)
                }
                CredentialEditMode.Replace -> stringResource(R.string.credential_replace_on_save)
                CredentialEditMode.Clear -> stringResource(R.string.credential_clear_on_save)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { TranslationProviderEditUiIntent.ShowApiKeyEditor.emit() }, shape = RoundedCornerShape(10.dp)) {
                val replacing = form.hasExistingApiKey || form.apiKeyEditMode == CredentialEditMode.Replace
                Text(stringResource(if (replacing) R.string.credential_replace else R.string.credential_set))
            }
            if (form.hasExistingApiKey && form.apiKeyEditMode != CredentialEditMode.Clear) {
                TextButton(onClick = { TranslationProviderEditUiIntent.ClearApiKey.emit() }) {
                    Text(stringResource(R.string.credential_clear), color = MaterialTheme.colorScheme.error)
                }
            }
            if (form.apiKeyEditMode != CredentialEditMode.KeepExisting) {
                TextButton(onClick = { TranslationProviderEditUiIntent.KeepExistingApiKey.emit() }) {
                    Text(stringResource(R.string.credential_undo_change))
                }
            }
        }
    }
}

@Composable
private fun LlmProviderField(selected: LlmProviderOption?, noneConfigured: Boolean, emit: TranslationProviderEditUiIntent.() -> Unit) {
    RpSettingsValueTile(
        title = stringResource(R.string.translation_provider_llm),
        value = selected?.name ?: stringResource(R.string.translation_provider_llm_choose),
        icon = Icons.Rounded.Psychology,
        enabled = !noneConfigured,
        onClick = { TranslationProviderEditUiIntent.ShowLlmProviderPicker.emit() }
    )
    Text(
        text = stringResource(if (noneConfigured) R.string.translation_provider_llm_none else R.string.translation_provider_llm_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TestPanel(state: TranslationProviderTestState, emit: TranslationProviderEditUiIntent.() -> Unit) {
    RpPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.translation_provider_test_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = when (state) {
                        TranslationProviderTestState.None -> stringResource(R.string.translation_provider_test_desc)
                        TranslationProviderTestState.Testing -> stringResource(R.string.testing)
                        is TranslationProviderTestState.Success -> stringResource(R.string.translation_provider_test_success, state.translation)
                        is TranslationProviderTestState.Failed -> stringResource(state.failure.messageRes())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (state) {
                        is TranslationProviderTestState.Failed -> MaterialTheme.colorScheme.error
                        is TranslationProviderTestState.Success -> MaterialTheme.himitsuColors.success
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            val testing = state is TranslationProviderTestState.Testing
            OutlinedButton(
                shape = RoundedCornerShape(10.dp),
                onClick = { (if (testing) TranslationProviderEditUiIntent.CancelTest else TranslationProviderEditUiIntent.TestClick).emit() }
            ) {
                Icon(if (testing) Icons.Rounded.Close else Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(if (testing) R.string.cancel else R.string.test))
            }
        }
    }
}

@Composable
private fun SaveButton(uiState: TranslationProviderEditUiState.Normal, emit: TranslationProviderEditUiIntent.() -> Unit) {
    TextButton(enabled = !uiState.isSaving, onClick = { TranslationProviderEditUiIntent.SaveClick.emit() }) {
        if (uiState.isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(stringResource(if (uiState.isCreate) R.string.create else R.string.save))
    }
}

@Composable
private fun EditDialog(uiState: TranslationProviderEditUiState.Normal, emit: TranslationProviderEditUiIntent.() -> Unit) {
    val dismiss = { TranslationProviderEditUiIntent.DismissDialog.emit() }
    when (uiState.dialogState) {
        TranslationProviderEditDialogState.None -> Unit
        TranslationProviderEditDialogState.ApiKeyEditor -> {
            var value by remember { mutableStateOf("") }
            AppInputDialog(
                onDismissRequest = dismiss,
                title = stringResource(R.string.api_key_editor_title),
                subtitle = stringResource(R.string.credential_editor_privacy_note),
                value = value,
                onValueChange = { value = it },
                label = stringResource(R.string.api_key),
                password = true,
                confirmEnabled = value.isNotBlank(),
                onConfirm = { TranslationProviderEditUiIntent.ConfirmApiKey(value).emit() }
            )
        }
        TranslationProviderEditDialogState.LlmProviderPicker -> AppDialogScaffold(
            onDismissRequest = dismiss,
            title = stringResource(R.string.translation_provider_llm),
            badgeIcon = Icons.Rounded.Psychology,
            compactHeader = true,
            confirmText = "",
            onConfirm = null,
            scrollableContent = true
        ) {
            uiState.llmProviders.forEach { option ->
                TranslationChoiceRow(
                    title = option.name,
                    subtitle = if (option.isLocal) stringResource(R.string.translation_provider_llm_local) else null,
                    selected = option.id == uiState.form.llmProviderId,
                    onClick = { TranslationProviderEditUiIntent.SelectLlmProvider(option.id).emit() }
                )
            }
        }
        TranslationProviderEditDialogState.UnsavedChanges -> AppDangerDialog(
            onDismissRequest = dismiss,
            title = stringResource(R.string.unsaved_changes_title),
            message = stringResource(R.string.unsaved_changes_message),
            confirmText = stringResource(R.string.discard_changes),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { TranslationProviderEditUiIntent.ConfirmDiscardChanges.emit() }
        )
    }
}

private fun TranslationProviderType.sendsNoteRes(): Int = when (this) {
    TranslationProviderType.LibreTranslate -> R.string.translation_provider_sends_libre
    TranslationProviderType.DeepL -> R.string.translation_provider_sends_deepl
    TranslationProviderType.GoogleCloud -> R.string.translation_provider_sends_google
    TranslationProviderType.LlmProvider -> R.string.translation_provider_sends_llm
}

private fun TranslationFailure.messageRes(): Int = when (this) {
    TranslationFailure.Network -> R.string.translation_provider_test_failed_network
    TranslationFailure.Auth -> R.string.translation_provider_test_failed_auth
    TranslationFailure.Quota -> R.string.translation_provider_test_failed_quota
    TranslationFailure.Unsupported -> R.string.translation_provider_test_failed_unsupported
    TranslationFailure.Invalid -> R.string.translation_provider_test_failed_invalid
}

@HimitsuPhonePreviews
@Composable
private fun TranslationProviderEditLayoutPreview() {
    AppTheme(dynamicColor = false) {
        TranslationProviderEditLayout(
            uiState = TranslationProviderEditUiState.Normal(
                isCreate = true,
                form = TranslationProviderEditForm(type = TranslationProviderType.LibreTranslate, baseUrl = "http://192.168.1.20:5000"),
                llmProviders = emptyList(),
                testState = TranslationProviderTestState.Success("¡Hola! El servicio de traducción funciona.")
            ),
            emit = {}
        )
    }
}
