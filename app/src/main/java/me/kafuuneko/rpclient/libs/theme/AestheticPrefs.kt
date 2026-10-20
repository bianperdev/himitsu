// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.theme

import com.chibatching.kotpref.KotprefModel

/**
 * Appearance preferences owned by the aesthetic track, kept out of [me.kafuuneko.rpclient.libs.AppModel].
 *
 * Holds only non-sensitive display choices. Read and write it through [AppThemeManager].
 */
object AestheticPrefs : KotprefModel() {
    /** Material You dynamic color. Off by default: the Himitsu palette is the identity. */
    var dynamicColor by booleanPref(default = false)
}
