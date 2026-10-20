// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession

/**
 * Keeps typing and copying on a page private (P-11), for every text field and selectable text
 * in [content] without changing them one by one:
 * - text copied or cut through Compose (the selection toolbar, text fields) is marked
 *   sensitive, as [SensitiveClip] does for the explicit copy actions
 * - keyboards are asked not to learn from what is typed (`IME_FLAG_NO_PERSONALIZED_LEARNING`).
 *   It is a request: Gboard honors it, other keyboards may not.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PrivateTextInput(content: @Composable () -> Unit) {
    val platform = LocalClipboard.current
    val clipboard = remember(platform) { SensitiveClipboard(platform) }
    CompositionLocalProvider(LocalClipboard provides clipboard) {
        InterceptPlatformTextInput(NoPersonalizedLearning, content)
    }
}

/** Marks every clip as sensitive before it reaches the system clipboard. */
private class SensitiveClipboard(private val mPlatform: Clipboard) : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? = mPlatform.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        mPlatform.setClipEntry(clipEntry?.let { ClipEntry(SensitiveClip.markSensitive(it.clipData)) })
    }

    override val nativeClipboard get() = mPlatform.nativeClipboard
}

/** Adds the no-learning flag to the editor of every text field. */
@OptIn(ExperimentalComposeUiApi::class)
private object NoPersonalizedLearning : PlatformTextInputInterceptor {
    override suspend fun interceptStartInputMethod(
        request: PlatformTextInputMethodRequest,
        nextHandler: PlatformTextInputSession
    ): Nothing = nextHandler.startInputMethod(object : PlatformTextInputMethodRequest {
        override fun createInputConnection(outAttributes: EditorInfo): InputConnection =
            request.createInputConnection(outAttributes).also {
                outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
    })
}
