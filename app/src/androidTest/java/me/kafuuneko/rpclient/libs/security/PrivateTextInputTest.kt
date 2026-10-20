// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Himitsu (P-11): what Compose copies on a page reaches the clipboard marked sensitive.
 *
 * The system does not hand the flag back to a reader, so a recording clipboard stands in for
 * the platform one. Uses placeholder text only.
 */
@RunWith(AndroidJUnit4::class)
class PrivateTextInputTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun clipsCopiedThroughComposeAreMarkedSensitive() {
        val platform = RecordingClipboard()
        lateinit var clipboard: Clipboard
        compose.setContent {
            CompositionLocalProvider(LocalClipboard provides platform) {
                PrivateTextInput { clipboard = LocalClipboard.current }
            }
        }

        runBlocking { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("label", "placeholder"))) }

        val extras = requireNotNull(platform.last).clipData.description.extras
        assertTrue(extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true)
    }

    private class RecordingClipboard : Clipboard {
        var last: ClipEntry? = null

        override suspend fun getClipEntry(): ClipEntry? = last

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            last = clipEntry
        }

        override val nativeClipboard get() = throw UnsupportedOperationException()
    }
}
