// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.translationsettings.model

import java.util.Locale
import me.kafuuneko.rpclient.libs.translation.model.TranslationProviderType

/**
 * A language the user can choose.
 *
 * @property tag BCP 47 primary tag; empty means "follow the app language"
 * @property name the language's own name ("Español"), which every reader can recognise
 */
data class TranslationLanguageOption(val tag: String, val name: String) {
    companion object {
        /** Languages offered as "your language": the app's own, then other widely spoken ones. */
        val TAGS = listOf(
            "en", "es", "pt", "zh", "ja", "ko", "de", "fr", "it", "ru", "id", "vi",
            "ar", "hi", "tr", "pl", "nl", "uk", "th", "sv", "cs", "el", "he", "ro", "hu", "fi", "da", "nb", "ms", "fil"
        )

        /** The option of [tag], named in its own language. */
        fun of(tag: String): TranslationLanguageOption {
            val locale = Locale.forLanguageTag(tag)
            val name = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
            return TranslationLanguageOption(tag, name.ifBlank { tag })
        }
    }
}

/** A configured translation service as the list shows it; never holds the key. */
data class TranslationProviderListItem(
    val id: Long,
    val name: String,
    val type: TranslationProviderType,
    /** Host of the endpoint, or the AI provider's name; empty when the service's default is used. */
    val endpoint: String,
    /** The service is reached over plain HTTP on another host. */
    val unencrypted: Boolean
)
