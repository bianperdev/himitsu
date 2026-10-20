// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.kafuuneko.rpclient.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the calculator decoy through its keypad, as a user would. */
@RunWith(AndroidJUnit4::class)
class CalculatorScreenAndroidTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<DisguiseActivity>()

    private fun press(vararg labels: String) = labels.forEach { composeRule.onNodeWithText(it).performClick() }

    private fun pressSymbol(description: Int) =
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(description)).performClick()

    @Test
    fun computesWithPrecedenceAndShowsAPreview() {
        press("1", "2")
        pressSymbol(R.string.disguise_calc_plus)
        press("3")
        pressSymbol(R.string.disguise_calc_multiply)
        press("4")
        composeRule.onNodeWithText("24").assertExists()
        pressSymbol(R.string.disguise_calc_equals)
        composeRule.onNodeWithText("24").assertExists()
    }

    @Test
    fun dividingByZeroSaysSo() {
        press("7")
        pressSymbol(R.string.disguise_calc_divide)
        press("0")
        pressSymbol(R.string.disguise_calc_equals)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.disguise_calc_error_divide_by_zero)).assertExists()
    }
}
