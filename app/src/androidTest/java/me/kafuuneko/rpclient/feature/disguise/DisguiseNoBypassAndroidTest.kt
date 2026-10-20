// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise

import android.accessibilityservice.AccessibilityService
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.main.MainActivity
import me.kafuuneko.rpclient.libs.disguise.Appearance
import me.kafuuneko.rpclient.libs.disguise.secret.DisguiseSecretStore
import me.kafuuneko.rpclient.libs.security.lock.AppLockManager
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Proves on a device that no path from the calculator decoy reaches Himitsu without the system
 * authentication: the right code and the recovery gesture only show the prompt, a wrong code
 * shows nothing, and canceling the prompt leaves the decoy on screen.
 *
 * Needs a device with a screen lock (the emulator of the track has a PIN); without one, the
 * gate screen's documented "continue without a screen lock" path applies instead.
 */
@RunWith(AndroidJUnit4::class)
class DisguiseNoBypassAndroidTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<DisguiseActivity>()

    private val lockManager: AppLockManager = GlobalContext.get().get()
    private val secretStore: DisguiseSecretStore = GlobalContext.get().get()

    @Before
    fun setCode() {
        assumeTrue("needs a device screen lock", lockManager.isDeviceSecure())
        runBlocking { secretStore.set(Appearance.Calculator, "482913".toCharArray()) }
    }

    @After
    fun clean() {
        closePrompt()
        secretStore.clear()
    }

    @Test
    fun theRightCodeOnlyShowsThePrompt() {
        type("482913")
        pressSymbol(R.string.disguise_calc_equals)
        composeRule.waitUntil(10_000) { lockManager.isAuthenticating }
        assertHimitsuNeverOpened()
    }

    @Test
    fun aWrongCodeShowsNothing() {
        type("482914")
        pressSymbol(R.string.disguise_calc_equals)
        // Longer than a key derivation takes on the emulator
        Thread.sleep(4_000)
        assertFalse(lockManager.isAuthenticating)
        assertHimitsuNeverOpened()
    }

    @Test
    fun theRecoveryGestureOnlyShowsThePrompt() {
        pressSymbol(R.string.disguise_menu_more)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.disguise_menu_about)).performClick()
        val version = composeRule.activity.getString(R.string.disguise_about_version, versionName())
        composeRule.onNodeWithText(version).performTouchInput {
            down(center)
            advanceEventTime(5_500)
            up()
        }
        composeRule.waitUntil(10_000) { lockManager.isAuthenticating }
        assertHimitsuNeverOpened()
    }

    @Test
    fun cancelingThePromptLeavesTheDecoy() {
        type("482913")
        pressSymbol(R.string.disguise_calc_equals)
        composeRule.waitUntil(10_000) { lockManager.isAuthenticating }
        closePrompt()
        composeRule.waitUntil(10_000) { !lockManager.isAuthenticating }
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.disguise_calc_equals)).assertExists()
        assertHimitsuNeverOpened()
    }

    private fun type(digits: String) = digits.forEach { composeRule.onNodeWithText(it.toString()).performClick() }

    private fun pressSymbol(description: Int) =
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(description)).performClick()

    private fun closePrompt() {
        if (!lockManager.isAuthenticating) return
        // The flag is set before the system draws the prompt; Back must reach the prompt, not the decoy
        Thread.sleep(2_500)
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        Thread.sleep(1_500)
    }

    private fun versionName(): String = composeRule.activity.packageManager
        .getPackageInfo(composeRule.activity.packageName, 0).versionName.orEmpty()

    private fun assertHimitsuNeverOpened() {
        var mainVisible = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            mainVisible = listOf(Stage.RESUMED, Stage.STARTED, Stage.PAUSED).any { stage ->
                monitor.getActivitiesInStage(stage).any { it is MainActivity }
            }
        }
        assertFalse("Himitsu opened without authentication", mainVisible)
    }
}
