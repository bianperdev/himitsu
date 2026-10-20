// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle

/**
 * Clipboard content marked as sensitive (P-11).
 *
 * Android 13 and later then hide the text in the clipboard preview and keep keyboards from
 * suggesting it. Use it for chats, stories, characters, prompts and request logs.
 */
object SensitiveClip {
    /** Same as [ClipData.newPlainText], with the sensitive flag set. */
    fun plainText(label: CharSequence, text: CharSequence): ClipData = markSensitive(ClipData.newPlainText(label, text))

    /** Sets the sensitive flag on [clip], keeping its other extras. */
    fun markSensitive(clip: ClipData): ClipData = clip.apply {
        description.extras = (description.extras ?: PersistableBundle()).apply {
            putBoolean(sensitiveExtraKey(), true)
        }
    }

    private fun sensitiveExtraKey(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            // The same key, readable by keyboards that backported the behavior
            "android.content.extra.IS_SENSITIVE"
        }
}
