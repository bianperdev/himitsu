// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise

import androidx.annotation.StringRes
import me.kafuuneko.rpclient.R

/** Package of the launcher aliases, which is the manifest namespace, not the application ID. */
private const val ALIAS_PACKAGE = "me.kafuuneko.rpclient.launcher"

/**
 * How Himitsu shows up in the launcher: its own look or one of the decoys.
 *
 * Each appearance is one `<activity-alias>` in the manifest, and exactly one alias is enabled at
 * a time. [id] is persisted and launchers store [aliasName] in home screen icons, so neither may
 * change once a version has shipped.
 *
 * @property iconRes the alias icon, a mipmap resource
 * @property enabledByDefault the alias state declared in the manifest
 */
enum class Appearance(
    val id: String,
    val aliasName: String,
    @param:StringRes val labelRes: Int,
    val iconRes: Int,
    val enabledByDefault: Boolean
) {
    Himitsu("himitsu", "$ALIAS_PACKAGE.HimitsuLauncher", R.string.app_name, R.mipmap.ic_launcher, true),
    Calculator("calculator", "$ALIAS_PACKAGE.CalculatorLauncher", R.string.disguise_calculator_name, R.mipmap.ic_launcher_calculator, false),
    Compass("compass", "$ALIAS_PACKAGE.CompassLauncher", R.string.disguise_compass_name, R.mipmap.ic_launcher_compass, false),
    Converter("converter", "$ALIAS_PACKAGE.ConverterLauncher", R.string.disguise_converter_name, R.mipmap.ic_launcher_converter, false);

    /** Whether this appearance is a decoy rather than Himitsu itself. */
    val isDecoy: Boolean
        get() = this != Himitsu

    companion object {
        /** The appearance stored under [id], or [Himitsu] for an unknown or empty value. */
        fun fromId(id: String): Appearance = entries.firstOrNull { it.id == id } ?: Himitsu
    }
}
