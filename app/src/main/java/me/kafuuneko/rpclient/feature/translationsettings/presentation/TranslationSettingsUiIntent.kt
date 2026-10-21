// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.translationsettings.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** User actions on the Translation settings screen. */
sealed class TranslationSettingsUiIntent {
    data object Init : TranslationSettingsUiIntent()
    data object Resume : TranslationSettingsUiIntent()
    data object Back : TranslationSettingsUiIntent()

    data class ToggleEnabled(val enabled: Boolean) : TranslationSettingsUiIntent()
    data object ShowLanguagePicker : TranslationSettingsUiIntent()

    /** An empty [tag] follows the app language. */
    data class SelectLanguage(val tag: String) : TranslationSettingsUiIntent()
    data object ShowDefaultProviderPicker : TranslationSettingsUiIntent()
    data class SelectDefaultProvider(val providerId: Long?) : TranslationSettingsUiIntent()
    data class ToggleTranslateOwnMessages(val enabled: Boolean) : TranslationSettingsUiIntent()

    data object AddProvider : TranslationSettingsUiIntent()
    data class EditProvider(val providerId: Long) : TranslationSettingsUiIntent()
    data class ShowDeleteProviderDialog(val providerId: Long) : TranslationSettingsUiIntent()
    data object ConfirmDeleteProvider : TranslationSettingsUiIntent()

    data object AcceptDataNotice : TranslationSettingsUiIntent()
    data object OpenLibreTranslate : TranslationSettingsUiIntent()
    data object DismissDialog : TranslationSettingsUiIntent()
}

/** One-time actions of the Translation settings host. */
sealed class TranslationSettingsViewEvent : IViewEvent {
    /** Opens [url] in the external browser. */
    data class OpenExternalLink(val url: String) : TranslationSettingsViewEvent()
}
