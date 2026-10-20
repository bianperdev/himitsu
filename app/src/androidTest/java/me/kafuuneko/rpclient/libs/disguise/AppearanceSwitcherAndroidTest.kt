// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Switches the real launcher aliases through the package manager. Always restores Himitsu, so a
 * failed run never leaves the emulator with a decoy icon.
 */
@RunWith(AndroidJUnit4::class)
class AppearanceSwitcherAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val switcher = AppearanceSwitcher(context)

    @Before
    fun onlyOnEmulator() = assumeEmulator()

    @After
    fun restoreHimitsu() {
        if (!isEmulator()) return
        switcher.apply(Appearance.Himitsu)
    }

    @Test
    fun eachAppearanceLeavesExactlyItsAliasInTheLauncher() {
        for (target in Appearance.entries) {
            switcher.apply(target)
            assertEquals(target, switcher.current)
            assertEquals(listOf(target.aliasName), launcherEntries())
        }
    }

    @Test
    fun reconcileRepairsAnInterruptedSwitch() {
        switcher.apply(Appearance.Calculator)
        // Simulates the process dying after the new alias was enabled but before the old one was disabled
        setAlias(Appearance.Himitsu, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        assertEquals(2, launcherEntries().size)

        switcher.reconcile()

        assertEquals(listOf(Appearance.Calculator.aliasName), launcherEntries())
    }

    @Test
    fun himitsuRestoresTheManifestDefaults() {
        switcher.apply(Appearance.Compass)
        switcher.apply(Appearance.Himitsu)
        for (appearance in Appearance.entries) {
            val state = context.packageManager.getComponentEnabledSetting(ComponentName(context.packageName, appearance.aliasName))
            assertEquals(appearance.name, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, state)
        }
    }

    /** Class names of this app's activities the launcher would list. */
    private fun launcherEntries(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        return context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.name }
    }

    private fun setAlias(appearance: Appearance, state: Int) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context.packageName, appearance.aliasName),
            state,
            PackageManager.DONT_KILL_APP
        )
    }
}
