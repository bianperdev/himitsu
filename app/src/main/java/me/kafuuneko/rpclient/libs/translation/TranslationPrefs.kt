// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation

import com.chibatching.kotpref.KotprefModel

/**
 * Device-level translation settings. None is sensitive: provider keys live in the encrypted
 * database, and these are only switches, a language tag and a row id.
 */
object TranslationPrefs : KotprefModel() {
    override val kotprefName: String = "translation"

    /** Master switch; off by default, so nothing is ever sent to a translation service unasked. */
    var enabled by booleanPref(default = false)

    /** The user's language (BCP 47 primary tag); empty follows the app language. */
    var userLanguage by stringPref(default = "")

    /** The provider used by chats without their own; [NO_PROVIDER] when none is chosen. */
    var defaultProviderId by longPref(default = NO_PROVIDER)

    /** Translate what the user types into the conversation language before sending. */
    var translateOwnMessages by booleanPref(default = true)

    const val NO_PROVIDER = -1L
}
