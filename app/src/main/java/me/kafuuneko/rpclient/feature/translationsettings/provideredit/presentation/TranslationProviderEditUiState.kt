// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.translationsettings.provideredit.presentation

import me.kafuuneko.rpclient.feature.llmprovideredit.model.CredentialEditMode
import me.kafuuneko.rpclient.libs.translation.TranslationFailure
import me.kafuuneko.rpclient.libs.translation.model.TranslationProviderType

/** State of the translation service editor. */
sealed class TranslationProviderEditUiState {
    data object None : TranslationProviderEditUiState()

    data class Normal(
        val isCreate: Boolean,
        val form: TranslationProviderEditForm,
        val initialForm: TranslationProviderEditForm = form,
        /** AI providers that can translate, by id and name only. */
        val llmProviders: List<LlmProviderOption>,
        val testState: TranslationProviderTestState = TranslationProviderTestState.None,
        val isSaving: Boolean = false,
        val dialogState: TranslationProviderEditDialogState = TranslationProviderEditDialogState.None
    ) : TranslationProviderEditUiState() {
        val selectedLlmProvider: LlmProviderOption?
            get() = llmProviders.firstOrNull { it.id == form.llmProviderId }
    }

    data class Finished(val previous: TranslationProviderEditUiState) : TranslationProviderEditUiState()
}

/**
 * The editable fields. The API key is never here: the form only knows whether one exists and
 * what will happen to it; the ViewModel holds the value.
 */
data class TranslationProviderEditForm(
    val id: Long = 0L,
    val createTime: Long = 0L,
    val name: String = "",
    val type: TranslationProviderType = TranslationProviderType.LibreTranslate,
    val baseUrl: String = "",
    val hasExistingApiKey: Boolean = false,
    val apiKeyEditMode: CredentialEditMode = CredentialEditMode.KeepExisting,
    val llmProviderId: Long? = null
) {
    /** Whether the type talks to an HTTP endpoint with its own key (not an AI provider). */
    val usesHttpEndpoint: Boolean
        get() = type != TranslationProviderType.LlmProvider

    /** The same form with trimmed text, to compare for unsaved changes. */
    fun normalized(): TranslationProviderEditForm = copy(name = name.trim(), baseUrl = baseUrl.trim())
}

/** An AI provider that can be used as translator. */
data class LlmProviderOption(val id: Long, val name: String, val isLocal: Boolean)

/** Connection test with a fixed sample sentence. */
sealed class TranslationProviderTestState {
    data object None : TranslationProviderTestState()
    data object Testing : TranslationProviderTestState()
    data class Success(val translation: String) : TranslationProviderTestState() {
        override fun toString(): String = "Success([REDACTED])"
    }
    data class Failed(val failure: TranslationFailure) : TranslationProviderTestState()
}

sealed class TranslationProviderEditDialogState {
    data object None : TranslationProviderEditDialogState()
    data object ApiKeyEditor : TranslationProviderEditDialogState()
    data object LlmProviderPicker : TranslationProviderEditDialogState()
    data object UnsavedChanges : TranslationProviderEditDialogState()
}

/** User actions on the translation service editor. */
sealed class TranslationProviderEditUiIntent {
    data class Init(val providerId: Long?) : TranslationProviderEditUiIntent()
    data object Back : TranslationProviderEditUiIntent()
    data class SelectType(val type: TranslationProviderType) : TranslationProviderEditUiIntent()
    data class ChangeName(val value: String) : TranslationProviderEditUiIntent()
    data class ChangeBaseUrl(val value: String) : TranslationProviderEditUiIntent()
    data object ShowApiKeyEditor : TranslationProviderEditUiIntent()

    /** A new key typed in the masked dialog. [toString] never prints it. */
    class ConfirmApiKey(val value: String) : TranslationProviderEditUiIntent() {
        override fun toString(): String = "ConfirmApiKey([REDACTED])"
    }

    data object ClearApiKey : TranslationProviderEditUiIntent()
    data object KeepExistingApiKey : TranslationProviderEditUiIntent()
    data object ShowLlmProviderPicker : TranslationProviderEditUiIntent()
    data class SelectLlmProvider(val id: Long) : TranslationProviderEditUiIntent()
    data object TestClick : TranslationProviderEditUiIntent()
    data object CancelTest : TranslationProviderEditUiIntent()
    data object SaveClick : TranslationProviderEditUiIntent()
    data object ConfirmDiscardChanges : TranslationProviderEditUiIntent()
    data object DismissDialog : TranslationProviderEditUiIntent()
}
