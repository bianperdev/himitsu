// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise

import com.chibatching.kotpref.KotprefModel

/**
 * Non-sensitive disguise settings of the `feat/disguise` track.
 *
 * The secret code never lives here; it is kept as a salted hash in its own file. Values are
 * written with `commit` because the launcher aliases are switched right after the appearance is
 * saved, and the process may be killed by that switch on some devices.
 */
object DisguisePrefs : KotprefModel() {
    override val kotprefName: String = "himitsu_disguise"

    override val commitAllPropertiesByDefault: Boolean = true

    /** [Appearance.id] of the appearance the user chose. Himitsu by default: disguise is off. */
    var appearanceId by stringPref(default = Appearance.Himitsu.id)
}
