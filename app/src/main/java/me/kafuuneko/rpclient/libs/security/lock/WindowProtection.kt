// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.lock

import android.app.Activity
import android.os.Build
import android.view.WindowManager

/**
 * Applies the screen privacy settings to an Activity window (P-12).
 *
 * - With "hide content" on, `FLAG_SECURE` blocks screenshots and screen recording, and the
 *   recent apps view shows a blank preview on every Android version.
 * - With only the app lock on, Android 13 and later hide the recents preview without blocking
 *   screenshots, so the lock is not defeated by a thumbnail.
 */
object WindowProtection {
    /** Applies the current settings to [activity]. Safe to call on every resume. */
    fun apply(activity: Activity) {
        if (SecurityPrefs.secureScreenEnabled) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hidePreview = SecurityPrefs.secureScreenEnabled || SecurityPrefs.appLockEnabled
            activity.setRecentsScreenshotEnabled(!hidePreview)
        }
    }
}
