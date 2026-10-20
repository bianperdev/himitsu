// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasSwitchPlanTest {
    /** Every way the aliases can be before a switch: one enabled, or two after an interrupted switch. */
    private val startStates: List<Set<Appearance>> =
        Appearance.entries.map { setOf(it) } +
            Appearance.entries.flatMap { a -> Appearance.entries.filter { it != a }.map { b -> setOf(a, b) } }

    @Test
    fun launcherNeverLosesItsLastEntryDuringASwitch() {
        for (start in startStates) {
            for (target in Appearance.entries) {
                val enabled = start.toMutableSet()
                for (step in aliasSwitchPlan(target)) {
                    if (step.enabled) enabled += step.appearance else enabled -= step.appearance
                    assertTrue("no launcher entry while switching $start to $target", enabled.isNotEmpty())
                }
                assertEquals(setOf(target), enabled)
            }
        }
    }
}
