// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.translationsettings.presentation

import me.kafuuneko.rpclient.feature.translationsettings.model.TranslationLanguageOption
import me.kafuuneko.rpclient.feature.translationsettings.model.TranslationProviderListItem

/** State of the Translation settings screen. */
sealed class TranslationSettingsUiState {
    data object None : TranslationSettingsUiState()

    /**
     * @property enabled master switch ([me.kafuuneko.rpclient.libs.translation.TranslationPrefs.enabled])
     * @property userLanguage the chosen language; an empty tag follows the app language
     * @property appLanguage the app language, shown on the "follow the app" option
     * @property providers the configured services, without keys
     * @property defaultProviderId the default service, or null when none is chosen
     */
    data class Normal(
        val enabled: Boolean,
        val userLanguage: TranslationLanguageOption,
        val appLanguage: TranslationLanguageOption,
        val languages: List<TranslationLanguageOption>,
        val translateOwnMessages: Boolean,
        val providers: List<TranslationProviderListItem>,
        val defaultProviderId: Long?,
        val dialogState: TranslationSettingsDialogState = TranslationSettingsDialogState.None
    ) : TranslationSettingsUiState() {
        val defaultProvider: TranslationProviderListItem?
            get() = providers.firstOrNull { it.id == defaultProviderId }
    }

    data class Finished(val previous: TranslationSettingsUiState) : TranslationSettingsUiState()
}

/** What the user was doing when the data notice appeared; it resumes once they accept. */
enum class TranslationDataNoticeAction { EnableTranslation, AddProvider }

/** Dialogs of the Translation settings screen. */
sealed class TranslationSettingsDialogState {
    data object None : TranslationSettingsDialogState()

    /** Explains what is sent and to whom, the first time translation is turned on or a service added. */
    data class DataNotice(val action: TranslationDataNoticeAction) : TranslationSettingsDialogState()

    data object LanguagePicker : TranslationSettingsDialogState()

    data object DefaultProviderPicker : TranslationSettingsDialogState()

    data class DeleteProvider(
        val providerId: Long,
        val providerName: String,
        val isDeleting: Boolean = false
    ) : TranslationSettingsDialogState()
}
