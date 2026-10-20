// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise

import android.app.Activity
import android.os.SystemClock
import android.view.KeyEvent
import me.kafuuneko.rpclient.libs.security.UnlockEntry
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Quick exit: shows the decoy over Himitsu at once.
 *
 * It opens the decoy in covering mode through [UnlockEntry], so going back to Himitsu needs the
 * shortcut and authentication, exactly as after a lock. With disguise off it does nothing.
 */
object QuickExit : KoinComponent {
    /** Maximum time between the two volume down presses. */
    private const val DOUBLE_PRESS_MILLIS = 400L

    private val mUnlockEntry by inject<UnlockEntry>()
    private var mLastVolumeDown = 0L

    /** Shows the decoy over [activity] if disguise is on. */
    fun open(activity: Activity) {
        if (isDisguiseActive()) mUnlockEntry.open(activity)
    }

    /**
     * Watches for a double press of volume down when the user turned it on. The first press
     * still changes the volume; only the second one is consumed.
     *
     * @return true when the event was used for the quick exit
     */
    fun onKeyEvent(activity: Activity, event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN || event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        if (!DisguisePrefs.quickExitVolumeKey || !isDisguiseActive()) return false
        val now = SystemClock.uptimeMillis()
        if (now - mLastVolumeDown > DOUBLE_PRESS_MILLIS) {
            mLastVolumeDown = now
            return false
        }
        mLastVolumeDown = 0L
        mUnlockEntry.open(activity)
        return true
    }
}
