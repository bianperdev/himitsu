// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.core

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import java.util.Locale
import me.kafuuneko.rpclient.libs.translation.TranslationPrefs

/**
 * The device-level settings [ChatTranslatorImpl] reads, behind an interface so unit tests run
 * without Kotpref or an Android context.
 */
interface TranslationCoreSettings {
    /** Master switch. */
    val enabled: Boolean

    /** The default provider id, or null when none is chosen. */
    val defaultProviderId: Long?

    /** Whether what the user types is translated before sending. */
    val translateOwnMessages: Boolean

    /** The user's language as a BCP 47 primary tag in lower case, for example "es". */
    fun userLanguage(): String
}

/**
 * [TranslationCoreSettings] backed by [TranslationPrefs]. An empty user language follows the app
 * language: the per-app locale on API 33+ when the user set one, else the default locale.
 */
class PrefsTranslationCoreSettings(private val context: Context) : TranslationCoreSettings {
    override val enabled: Boolean get() = TranslationPrefs.enabled

    override val defaultProviderId: Long?
        get() = TranslationPrefs.defaultProviderId.takeIf { it != TranslationPrefs.NO_PROVIDER }

    override val translateOwnMessages: Boolean get() = TranslationPrefs.translateOwnMessages

    override fun userLanguage(): String {
        val chosen = normalizeLanguage(TranslationPrefs.userLanguage)
        if (chosen != null) return chosen
        return normalizeLanguage(appLocale().language) ?: FALLBACK_LANGUAGE
    }

    private fun appLocale(): Locale {
        // The platform per-app language (Android 13+); AppCompat is not a dependency
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales != null && !locales.isEmpty) return locales[0]
        }
        return Locale.getDefault()
    }

    private companion object {
        const val FALLBACK_LANGUAGE = "en"
    }
}

/**
 * The BCP 47 primary tag of [tag] in lower case ("pt-BR" gives "pt"), with Java's legacy codes
 * mapped to the current ones ("in" gives "id"); null when [tag] holds no language.
 */
fun normalizeLanguage(tag: String?): String? {
    val primary = tag?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase(Locale.ROOT)
    if (primary.isNullOrEmpty() || primary == "und" || !primary.all { it in 'a'..'z' }) return null
    return when (primary) {
        "in" -> "id"
        "iw" -> "he"
        "ji" -> "yi"
        else -> primary
    }
}
