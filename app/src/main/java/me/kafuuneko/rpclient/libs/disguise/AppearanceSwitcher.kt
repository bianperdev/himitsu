// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Switches the launcher entry between Himitsu and the decoys.
 *
 * Responsibilities:
 * - save the chosen [Appearance] and enable its alias, disabling the others
 * - never leave the launcher without an entry: the new alias is enabled before the old one is
 *   disabled, in a single call on Android 13 and later
 * - [reconcile] repairs a switch the system interrupted, so the aliases always match the saved
 *   appearance
 *
 * Every alias is changed with `DONT_KILL_APP`, but some launchers and OEM builds still restart
 * the process; callers save their work and warn the user before [apply].
 */
class AppearanceSwitcher(private val mContext: Context) {
    private val mPackageManager: PackageManager
        get() = mContext.packageManager

    /** The appearance the user chose. */
    val current: Appearance
        get() = Appearance.fromId(DisguisePrefs.appearanceId)

    /** Saves [target] and makes its alias the only launcher entry. */
    fun apply(target: Appearance) {
        DisguisePrefs.appearanceId = target.id
        writeAliasStates(target)
    }

    /**
     * Makes the aliases match the saved appearance if they do not, for example after the
     * process died between enabling the new alias and disabling the old one.
     */
    fun reconcile() {
        val target = current
        if (Appearance.entries.all { isAliasEnabled(it) == (it == target) }) return
        writeAliasStates(target)
    }

    /** Whether the launcher alias of [appearance] is enabled right now. */
    fun isAliasEnabled(appearance: Appearance): Boolean =
        when (mPackageManager.getComponentEnabledSetting(componentOf(appearance))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> appearance.enabledByDefault
            else -> false
        }

    private fun writeAliasStates(target: Appearance) {
        val steps = aliasSwitchPlan(target)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val settings = steps.map {
                PackageManager.ComponentEnabledSetting(componentOf(it.appearance), componentState(it), PackageManager.DONT_KILL_APP)
            }
            mPackageManager.setComponentEnabledSettings(settings)
        } else {
            steps.forEach {
                mPackageManager.setComponentEnabledSetting(componentOf(it.appearance), componentState(it), PackageManager.DONT_KILL_APP)
            }
        }
    }

    private fun componentOf(appearance: Appearance): ComponentName = ComponentName(mContext.packageName, appearance.aliasName)

    /**
     * The manifest default is used whenever it already gives the wanted state, so turning
     * disguise off leaves every alias exactly as a fresh install has it.
     */
    private fun componentState(step: AliasStep): Int = when {
        step.enabled == step.appearance.enabledByDefault -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        step.enabled -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
}

/** One alias change in a switch. */
internal data class AliasStep(val appearance: Appearance, val enabled: Boolean)

/**
 * The alias changes that make [target] the only launcher entry, enabling steps first.
 *
 * Applied in this order, at least one alias stays enabled after every step, whatever state the
 * aliases were in before.
 */
internal fun aliasSwitchPlan(target: Appearance): List<AliasStep> =
    Appearance.entries
        .map { AliasStep(it, it == target) }
        .sortedByDescending { it.enabled }
