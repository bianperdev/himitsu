// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.translationsettings.provideredit

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.llmprovideredit.model.CredentialEditMode
import me.kafuuneko.rpclient.feature.translationsettings.provideredit.presentation.LlmProviderOption
import me.kafuuneko.rpclient.feature.translationsettings.provideredit.presentation.TranslationProviderEditDialogState
import me.kafuuneko.rpclient.feature.translationsettings.provideredit.presentation.TranslationProviderEditForm
import me.kafuuneko.rpclient.feature.translationsettings.provideredit.presentation.TranslationProviderEditUiIntent
import me.kafuuneko.rpclient.feature.translationsettings.provideredit.presentation.TranslationProviderEditUiState
import me.kafuuneko.rpclient.feature.translationsettings.provideredit.presentation.TranslationProviderTestState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.security.CleartextPolicy
import me.kafuuneko.rpclient.libs.translation.TranslationException
import me.kafuuneko.rpclient.libs.translation.TranslationFailure
import me.kafuuneko.rpclient.libs.translation.TranslationPrefs
import me.kafuuneko.rpclient.libs.translation.model.TranslationProvider
import me.kafuuneko.rpclient.libs.translation.model.TranslationProviderType
import me.kafuuneko.rpclient.libs.translation.provider.TranslationProviderRepository
import me.kafuuneko.rpclient.libs.translation.provider.toConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * ViewModel of the translation service editor.
 *
 * Responsibilities:
 * - edit the type, name, endpoint and key of a service, or the AI provider it reuses
 * - keep the API key out of UI state: the form only shows whether one is set, and the value
 *   lives in [mApiKeyReplacement] until it is saved, then is wiped
 * - test the connection by translating a fixed sample sentence, never chat text
 * - save with `update` for existing rows and warn when the endpoint uses plain HTTP
 */
class TranslationProviderEditViewModel :
    CoreViewModelWithEvent<TranslationProviderEditUiIntent, TranslationProviderEditUiState>(TranslationProviderEditUiState.None),
    KoinComponent {
    private val mRepository by inject<TranslationProviderRepository>()
    private val mLLMRepository by inject<LLMRepository>()
    private val mContext by inject<Context>()

    private var mInitialApiKey = ""
    private var mApiKeyReplacement: String? = null

    /** Extra headers of a stored row; not editable here, kept as they are on save. */
    private var mCustomHeadersJson = ""
    private var mTestJob: Job? = null

    @UiIntentObserver(TranslationProviderEditUiIntent.Init::class)
    private suspend fun onInit(intent: TranslationProviderEditUiIntent.Init) {
        if (!isStateOf<TranslationProviderEditUiState.None>()) return
        val provider = intent.providerId?.let { mRepository.getById(it) }
        mInitialApiKey = provider?.apiKey.orEmpty()
        mCustomHeadersJson = provider?.customHeadersJson.orEmpty()
        val llmProviders = mLLMRepository.getAllProviders().map {
            LlmProviderOption(id = it.id, name = it.name, isLocal = it.protocol == LLMProviderProtocol.Local)
        }
        val form = provider?.let {
            TranslationProviderEditForm(
                id = it.id,
                createTime = it.createTime,
                name = it.name,
                type = it.type,
                baseUrl = it.baseUrl,
                hasExistingApiKey = it.apiKey.isNotBlank(),
                llmProviderId = it.llmProviderId
            )
        } ?: TranslationProviderEditForm()
        TranslationProviderEditUiState.Normal(isCreate = provider == null, form = form, llmProviders = llmProviders).setup()
    }

    /** Leaving with unsaved changes asks first. */
    @UiIntentObserver(TranslationProviderEditUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<TranslationProviderEditUiState.Normal>() ?: return
        if (uiState.isSaving) return
        cancelTest()
        if (uiState.form.normalized() != uiState.initialForm.normalized()) {
            uiState.copy(testState = TranslationProviderTestState.None, dialogState = TranslationProviderEditDialogState.UnsavedChanges).setup()
            return
        }
        finishPage()
    }

    @UiIntentObserver(TranslationProviderEditUiIntent.SelectType::class)
    private fun onSelectType(intent: TranslationProviderEditUiIntent.SelectType) = updateForm { copy(type = intent.type) }

    @UiIntentObserver(TranslationProviderEditUiIntent.ChangeName::class)
    private fun onChangeName(intent: TranslationProviderEditUiIntent.ChangeName) = updateForm { copy(name = intent.value) }

    @UiIntentObserver(TranslationProviderEditUiIntent.ChangeBaseUrl::class)
    private fun onChangeBaseUrl(intent: TranslationProviderEditUiIntent.ChangeBaseUrl) = updateForm { copy(baseUrl = intent.value) }

    @UiIntentObserver(TranslationProviderEditUiIntent.ShowApiKeyEditor::class)
    private fun onShowApiKeyEditor() = showDialog(TranslationProviderEditDialogState.ApiKeyEditor)

    @UiIntentObserver(TranslationProviderEditUiIntent.ConfirmApiKey::class)
    private fun onConfirmApiKey(intent: TranslationProviderEditUiIntent.ConfirmApiKey) {
        if (intent.value.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.api_key_required).tryEmit()
            return
        }
        mApiKeyReplacement = intent.value.trim()
        updateForm(closeDialog = true) { copy(apiKeyEditMode = CredentialEditMode.Replace) }
    }

    @UiIntentObserver(TranslationProviderEditUiIntent.ClearApiKey::class)
    private fun onClearApiKey() {
        mApiKeyReplacement = null
        updateForm { copy(apiKeyEditMode = CredentialEditMode.Clear) }
    }

    @UiIntentObserver(TranslationProviderEditUiIntent.KeepExistingApiKey::class)
    private fun onKeepExistingApiKey() {
        mApiKeyReplacement = null
        updateForm { copy(apiKeyEditMode = CredentialEditMode.KeepExisting) }
    }

    @UiIntentObserver(TranslationProviderEditUiIntent.ShowLlmProviderPicker::class)
    private fun onShowLlmProviderPicker() = showDialog(TranslationProviderEditDialogState.LlmProviderPicker)

    @UiIntentObserver(TranslationProviderEditUiIntent.SelectLlmProvider::class)
    private fun onSelectLlmProvider(intent: TranslationProviderEditUiIntent.SelectLlmProvider) {
        val uiState = getOrNull<TranslationProviderEditUiState.Normal>() ?: return
        if (uiState.llmProviders.none { it.id == intent.id }) return
        updateForm(closeDialog = true) { copy(llmProviderId = intent.id) }
    }

    /**
     * Translates the fixed sample with the unsaved form, into the user's language (Spanish when
     * that is English, so the result shows a real translation).
     */
    @UiIntentObserver(TranslationProviderEditUiIntent.TestClick::class)
    private fun onTestClick() {
        val uiState = getOrNull<TranslationProviderEditUiState.Normal>() ?: return
        if (mTestJob?.isActive == true) return
        val provider = buildProviderOrNull(uiState) ?: return
        uiState.copy(testState = TranslationProviderTestState.Testing).setup()
        mTestJob = viewModelScope.launch {
            val result = try {
                val translation = withContext(Dispatchers.IO) { mRepository.test(provider.toConfig(), testTarget()) }
                TranslationProviderTestState.Success(translation)
            } catch (error: CancellationException) {
                throw error
            } catch (error: TranslationException) {
                TranslationProviderTestState.Failed(error.failure)
            } catch (_: Exception) {
                TranslationProviderTestState.Failed(TranslationFailure.Network)
            }
            getOrNull<TranslationProviderEditUiState.Normal>()?.copy(testState = result)?.setup()
        }
    }

    @UiIntentObserver(TranslationProviderEditUiIntent.CancelTest::class)
    private fun onCancelTest() {
        val uiState = getOrNull<TranslationProviderEditUiState.Normal>() ?: return
        cancelTest()
        uiState.copy(testState = TranslationProviderTestState.None).setup()
    }

    /** Saves the service; a plain-HTTP endpoint on another host gets the shared warning. */
    @UiIntentObserver(TranslationProviderEditUiIntent.SaveClick::class)
    private suspend fun onSaveClick() {
        val uiState = getOrNull<TranslationProviderEditUiState.Normal>() ?: return
        if (uiState.isSaving) return
        val provider = buildProviderOrNull(uiState) ?: return
        cancelTest()
        uiState.copy(isSaving = true, testState = TranslationProviderTestState.None).setup()
        try {
            withContext(Dispatchers.IO) { mRepository.save(provider) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            AppViewEvent.PopupToastMessageByResId(R.string.translation_provider_save_failed).tryEmit()
            getOrNull<TranslationProviderEditUiState.Normal>()?.copy(isSaving = false)?.setup()
            return
        }
        val message = when {
            provider.type != TranslationProviderType.LlmProvider && CleartextPolicy.isUnencryptedRemote(provider.baseUrl) ->
                R.string.security_cleartext_saved_warning
            uiState.isCreate -> R.string.translation_provider_created
            else -> R.string.translation_provider_saved
        }
        AppViewEvent.PopupToastMessageByResId(message).tryEmit()
        finishPage()
    }

    @UiIntentObserver(TranslationProviderEditUiIntent.ConfirmDiscardChanges::class)
    private fun onConfirmDiscardChanges() {
        if (!isStateOf<TranslationProviderEditUiState.Normal>()) return
        finishPage()
    }

    @UiIntentObserver(TranslationProviderEditUiIntent.DismissDialog::class)
    private fun onDismissDialog() = showDialog(TranslationProviderEditDialogState.None)

    override fun onCleared() {
        cancelTest()
        wipeKeys()
        super.onCleared()
    }

    /**
     * The row to save or test, or null after a toast explaining what is missing.
     *
     * - LibreTranslate needs a server address; DeepL and Google need a key; an AI provider
     *   translator needs an AI provider.
     * - A blank name takes the service's name, or the AI provider's.
     * - Edits keep the row id and creation time, so `update` replaces the same row.
     */
    private fun buildProviderOrNull(uiState: TranslationProviderEditUiState.Normal): TranslationProvider? {
        val form = uiState.form.normalized()
        val apiKey = when (form.apiKeyEditMode) {
            CredentialEditMode.KeepExisting -> mInitialApiKey
            CredentialEditMode.Replace -> mApiKeyReplacement.orEmpty()
            CredentialEditMode.Clear -> ""
        }
        // Validate the fields each type needs
        val error = when {
            form.usesHttpEndpoint && form.baseUrl.isNotEmpty() && form.baseUrl.toHttpUrlOrNull() == null -> R.string.translation_provider_base_url_invalid
            form.type == TranslationProviderType.LibreTranslate && form.baseUrl.isEmpty() -> R.string.translation_provider_base_url_required
            form.type in KEY_REQUIRED && apiKey.isBlank() -> R.string.api_key_required
            form.type == TranslationProviderType.LlmProvider && uiState.selectedLlmProvider == null -> R.string.translation_provider_llm_required
            else -> null
        }
        if (error != null) {
            AppViewEvent.PopupToastMessageByResId(error).tryEmit()
            return null
        }
        // Only the fields of the chosen type are kept, so a switched type leaves nothing behind
        val isLlm = form.type == TranslationProviderType.LlmProvider
        return TranslationProvider(
            id = form.id,
            name = form.name.ifEmpty { defaultName(form.type, uiState.selectedLlmProvider) },
            type = form.type,
            baseUrl = if (isLlm) "" else form.baseUrl,
            apiKey = if (isLlm) "" else apiKey,
            llmProviderId = if (isLlm) form.llmProviderId else null,
            customHeadersJson = if (isLlm) "" else mCustomHeadersJson,
            createTime = form.createTime
        )
    }

    private fun defaultName(type: TranslationProviderType, llmProvider: LlmProviderOption?): String = when (type) {
        TranslationProviderType.LlmProvider -> llmProvider?.name ?: getString(R.string.translation_provider_type_llm)
        TranslationProviderType.LibreTranslate -> getString(R.string.translation_provider_type_libretranslate)
        TranslationProviderType.DeepL -> getString(R.string.translation_provider_type_deepl)
        TranslationProviderType.GoogleCloud -> getString(R.string.translation_provider_type_google)
    }

    private fun getString(@StringRes resId: Int): String = mContext.getString(resId)

    private fun testTarget(): String {
        val language = TranslationPrefs.userLanguage.ifEmpty { Locale.getDefault().language }.ifEmpty { "es" }
        return if (language == TranslationProviderRepository.TEST_SAMPLE_LANGUAGE) "es" else language
    }

    /** Updates the form and drops a stale test result. */
    private fun updateForm(closeDialog: Boolean = false, transform: TranslationProviderEditForm.() -> TranslationProviderEditForm) {
        val uiState = getOrNull<TranslationProviderEditUiState.Normal>() ?: return
        if (uiState.isSaving) return
        cancelTest()
        uiState.copy(
            form = uiState.form.transform(),
            testState = TranslationProviderTestState.None,
            dialogState = if (closeDialog) TranslationProviderEditDialogState.None else uiState.dialogState
        ).setup()
    }

    private fun showDialog(dialog: TranslationProviderEditDialogState) {
        val uiState = getOrNull<TranslationProviderEditUiState.Normal>() ?: return
        uiState.copy(dialogState = dialog).setup()
    }

    private fun cancelTest() {
        mTestJob?.cancel()
        mTestJob = null
    }

    private fun finishPage() {
        wipeKeys()
        val current = uiStateFlow.value
        if (current is TranslationProviderEditUiState.Finished) return
        TranslationProviderEditUiState.Finished(current).setup()
    }

    private fun wipeKeys() {
        mApiKeyReplacement = null
        mInitialApiKey = ""
    }

    private companion object {
        val KEY_REQUIRED = setOf(TranslationProviderType.DeepL, TranslationProviderType.GoogleCloud)
    }
}
