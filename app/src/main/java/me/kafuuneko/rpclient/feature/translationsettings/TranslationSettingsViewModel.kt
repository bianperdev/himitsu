// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.translationsettings

import android.os.Bundle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.about.FlavorAboutChannel
import me.kafuuneko.rpclient.feature.translationsettings.model.TranslationLanguageOption
import me.kafuuneko.rpclient.feature.translationsettings.model.TranslationProviderListItem
import me.kafuuneko.rpclient.feature.translationsettings.presentation.TranslationDataNoticeAction
import me.kafuuneko.rpclient.feature.translationsettings.presentation.TranslationSettingsDialogState
import me.kafuuneko.rpclient.feature.translationsettings.presentation.TranslationSettingsUiIntent
import me.kafuuneko.rpclient.feature.translationsettings.presentation.TranslationSettingsUiState
import me.kafuuneko.rpclient.feature.translationsettings.presentation.TranslationSettingsViewEvent
import me.kafuuneko.rpclient.feature.translationsettings.provideredit.TranslationProviderEditActivity
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.security.CleartextPolicy
import me.kafuuneko.rpclient.libs.translation.TranslationPrefs
import me.kafuuneko.rpclient.libs.translation.model.TranslationProvider
import me.kafuuneko.rpclient.libs.translation.model.TranslationProviderType
import me.kafuuneko.rpclient.libs.translation.provider.TranslationProviderPrefs
import me.kafuuneko.rpclient.libs.translation.provider.TranslationProviderRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * ViewModel of the Translation settings screen.
 *
 * Responsibilities:
 * - the device-level switches of [TranslationPrefs]: master switch, the user's language, the
 *   default service and whether the user's own messages are translated
 * - the list of translation services, opening the editor and deleting with a confirmation
 * - the data notice: the first time translation is turned on or a service is added, it explains
 *   that chat text goes to the chosen service, and the action resumes only once accepted
 */
class TranslationSettingsViewModel :
    CoreViewModelWithEvent<TranslationSettingsUiIntent, TranslationSettingsUiState>(TranslationSettingsUiState.None),
    KoinComponent {
    private val mProviderRepository by inject<TranslationProviderRepository>()
    private val mLLMRepository by inject<LLMRepository>()

    @UiIntentObserver(TranslationSettingsUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<TranslationSettingsUiState.None>()) return
        val appLanguage = TranslationLanguageOption.of(Locale.getDefault().language.ifBlank { "en" })
        TranslationSettingsUiState.Normal(
            enabled = TranslationPrefs.enabled,
            userLanguage = languageOption(TranslationPrefs.userLanguage, appLanguage),
            appLanguage = appLanguage,
            languages = TranslationLanguageOption.TAGS.map(TranslationLanguageOption::of),
            translateOwnMessages = TranslationPrefs.translateOwnMessages,
            providers = emptyList(),
            defaultProviderId = null
        ).setup()
        refreshProviders()
    }

    /** Reloads the services, which the editor may have changed. */
    @UiIntentObserver(TranslationSettingsUiIntent.Resume::class)
    private suspend fun onResume() {
        if (!isStateOf<TranslationSettingsUiState.Normal>()) return
        refreshProviders()
    }

    @UiIntentObserver(TranslationSettingsUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<TranslationSettingsUiState.Normal>() ?: return
        TranslationSettingsUiState.Finished(uiState).setup()
    }

    /** Turning translation on shows the data notice first, until the user accepts it once. */
    @UiIntentObserver(TranslationSettingsUiIntent.ToggleEnabled::class)
    private fun onToggleEnabled(intent: TranslationSettingsUiIntent.ToggleEnabled) {
        val uiState = getOrNull<TranslationSettingsUiState.Normal>() ?: return
        if (intent.enabled && !TranslationProviderPrefs.dataNoticeAccepted) {
            uiState.copy(dialogState = TranslationSettingsDialogState.DataNotice(TranslationDataNoticeAction.EnableTranslation)).setup()
            return
        }
        TranslationPrefs.enabled = intent.enabled
        uiState.copy(enabled = intent.enabled).setup()
    }

    @UiIntentObserver(TranslationSettingsUiIntent.ShowLanguagePicker::class)
    private fun onShowLanguagePicker() = showDialog(TranslationSettingsDialogState.LanguagePicker)

    @UiIntentObserver(TranslationSettingsUiIntent.SelectLanguage::class)
    private fun onSelectLanguage(intent: TranslationSettingsUiIntent.SelectLanguage) {
        val uiState = getOrNull<TranslationSettingsUiState.Normal>() ?: return
        if (intent.tag.isNotEmpty() && intent.tag !in TranslationLanguageOption.TAGS) return
        TranslationPrefs.userLanguage = intent.tag
        uiState.copy(
            userLanguage = languageOption(intent.tag, uiState.appLanguage),
            dialogState = TranslationSettingsDialogState.None
        ).setup()
    }

    @UiIntentObserver(TranslationSettingsUiIntent.ShowDefaultProviderPicker::class)
    private fun onShowDefaultProviderPicker() {
        val uiState = getOrNull<TranslationSettingsUiState.Normal>() ?: return
        if (uiState.providers.isEmpty()) return
        showDialog(TranslationSettingsDialogState.DefaultProviderPicker)
    }

    @UiIntentObserver(TranslationSettingsUiIntent.SelectDefaultProvider::class)
    private fun onSelectDefaultProvider(intent: TranslationSettingsUiIntent.SelectDefaultProvider) {
        val uiState = getOrNull<TranslationSettingsUiState.Normal>() ?: return
        val providerId = intent.providerId?.takeIf { id -> uiState.providers.any { it.id == id } }
        TranslationPrefs.defaultProviderId = providerId ?: TranslationPrefs.NO_PROVIDER
        uiState.copy(defaultProviderId = providerId, dialogState = TranslationSettingsDialogState.None).setup()
    }

    @UiIntentObserver(TranslationSettingsUiIntent.ToggleTranslateOwnMessages::class)
    private fun onToggleTranslateOwnMessages(intent: TranslationSettingsUiIntent.ToggleTranslateOwnMessages) {
        val uiState = getOrNull<TranslationSettingsUiState.Normal>() ?: return
        TranslationPrefs.translateOwnMessages = intent.enabled
        uiState.copy(translateOwnMessages = intent.enabled).setup()
    }

    /** Adding a service shows the data notice first, until the user accepts it once. */
    @UiIntentObserver(TranslationSettingsUiIntent.AddProvider::class)
    private fun onAddProvider() {
        val uiState = getOrNull<TranslationSettingsUiState.Normal>() ?: return
        if (!TranslationProviderPrefs.dataNoticeAccepted) {
            uiState.copy(dialogState = TranslationSettingsDialogState.DataNotice(TranslationDataNoticeAction.AddProvider)).setup()
            return
        }
        AppViewEvent.StartActivity(TranslationProviderEditActivity::class.java).tryEmit()
    }

    @UiIntentObserver(TranslationSettingsUiIntent.EditProvider::class)
    private fun onEditProvider(intent: TranslationSettingsUiIntent.EditProvider) {
        if (!isStateOf<TranslationSettingsUiState.Normal>()) return
        AppViewEvent.StartActivity(
            activity = TranslationProviderEditActivity::class.java,
            extras = Bundle().apply { putLong(TranslationProviderEditActivity.EXTRA_PROVIDER_ID, intent.providerId) }
        ).tryEmit()
    }

    @UiIntentObserver(TranslationSettingsUiIntent.ShowDeleteProviderDialog::class)
    private fun onShowDeleteProviderDialog(intent: TranslationSettingsUiIntent.ShowDeleteProviderDialog) {
        val uiState = getOrNull<TranslationSettingsUiState.Normal>() ?: return
        val provider = uiState.providers.firstOrNull { it.id == intent.providerId } ?: return
        showDialog(TranslationSettingsDialogState.DeleteProvider(provider.id, provider.name))
    }

    /** Deletes the service; translations it made stay, with no provider recorded. */
    @UiIntentObserver(TranslationSettingsUiIntent.ConfirmDeleteProvider::class)
    private suspend fun onConfirmDeleteProvider() {
        val uiState = getOrNull<TranslationSettingsUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? TranslationSettingsDialogState.DeleteProvider ?: return
        if (dialog.isDeleting) return
        uiState.copy(dialogState = dialog.copy(isDeleting = true)).setup()
        try {
            mProviderRepository.delete(dialog.providerId)
            AppViewEvent.PopupToastMessageByResId(R.string.translation_providers_deleted).tryEmit()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            AppViewEvent.PopupToastMessageByResId(R.string.translation_providers_delete_failed).tryEmit()
        }
        getOrNull<TranslationSettingsUiState.Normal>()?.copy(dialogState = TranslationSettingsDialogState.None)?.setup()
        refreshProviders()
    }

    /** Records that the user read the notice and resumes what they were doing. */
    @UiIntentObserver(TranslationSettingsUiIntent.AcceptDataNotice::class)
    private fun onAcceptDataNotice() {
        val uiState = getOrNull<TranslationSettingsUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? TranslationSettingsDialogState.DataNotice ?: return
        TranslationProviderPrefs.dataNoticeAccepted = true
        when (dialog.action) {
            TranslationDataNoticeAction.EnableTranslation -> {
                TranslationPrefs.enabled = true
                uiState.copy(enabled = true, dialogState = TranslationSettingsDialogState.None).setup()
            }
            TranslationDataNoticeAction.AddProvider -> {
                uiState.copy(dialogState = TranslationSettingsDialogState.None).setup()
                AppViewEvent.StartActivity(TranslationProviderEditActivity::class.java).tryEmit()
            }
        }
    }

    @UiIntentObserver(TranslationSettingsUiIntent.OpenLibreTranslate::class)
    private fun onOpenLibreTranslate() {
        if (!FlavorAboutChannel.linksExternalProjects) return
        TranslationSettingsViewEvent.OpenExternalLink(LIBRETRANSLATE_URL).tryEmit()
    }

    @UiIntentObserver(TranslationSettingsUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<TranslationSettingsUiState.Normal>() ?: return
        val dialog = uiState.dialogState
        if (dialog is TranslationSettingsDialogState.DeleteProvider && dialog.isDeleting) return
        uiState.copy(dialogState = TranslationSettingsDialogState.None).setup()
    }

    private fun showDialog(dialog: TranslationSettingsDialogState) {
        val uiState = getOrNull<TranslationSettingsUiState.Normal>() ?: return
        uiState.copy(dialogState = dialog).setup()
    }

    /** Loads the services as list items, with no key, and the default that is still valid. */
    private suspend fun refreshProviders() {
        val providers = mProviderRepository.getAll()
        // AI provider names are looked up once; a deleted AI provider shows no name
        val llmNames = if (providers.any { it.type == TranslationProviderType.LlmProvider }) {
            mLLMRepository.getAllProviders().associate { it.id to it.name }
        } else {
            emptyMap()
        }
        val items = providers.map { it.toListItem(llmNames) }
        val defaultId = TranslationPrefs.defaultProviderId.takeIf { id -> items.any { it.id == id } }
        val uiState = getOrNull<TranslationSettingsUiState.Normal>() ?: return
        uiState.copy(providers = items, defaultProviderId = defaultId).setup()
    }

    private fun TranslationProvider.toListItem(llmNames: Map<Long, String>): TranslationProviderListItem {
        val endpoint = when (type) {
            TranslationProviderType.LlmProvider -> llmProviderId?.let(llmNames::get).orEmpty()
            else -> baseUrl.trim().toHttpUrlOrNull()?.host.orEmpty()
        }
        return TranslationProviderListItem(
            id = id,
            name = name,
            type = type,
            endpoint = endpoint,
            unencrypted = type != TranslationProviderType.LlmProvider && CleartextPolicy.isUnencryptedRemote(baseUrl)
        )
    }

    private fun languageOption(tag: String, appLanguage: TranslationLanguageOption): TranslationLanguageOption =
        if (tag.isEmpty()) TranslationLanguageOption("", appLanguage.name) else TranslationLanguageOption.of(tag)

    private companion object {
        const val LIBRETRANSLATE_URL = "https://github.com/LibreTranslate/LibreTranslate"
    }
}
