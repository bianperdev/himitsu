// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap

/** Whether a decoy is the launcher entry right now. Reads one plain preference, safe on the main thread. */
fun isDisguiseActive(): Boolean = Appearance.fromId(DisguisePrefs.appearanceId).isDecoy

/**
 * Gives the task the decoy's label and icon in the recent apps screen.
 *
 * The task otherwise takes the label of its top Activity: `MainActivity` declares "Himitsu" and
 * the other screens inherit the application label, which recents, and screen readers reading
 * recents, would show. With disguise off nothing changes.
 */
object DisguiseTaskDescription {
    fun apply(activity: Activity) {
        val appearance = Appearance.fromId(DisguisePrefs.appearanceId)
        if (!appearance.isDecoy) return
        val label = activity.getString(appearance.labelRes)
        val description = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                ActivityManager.TaskDescription.Builder().setLabel(label).setIcon(appearance.iconRes).build()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                @Suppress("DEPRECATION")
                ActivityManager.TaskDescription(label, appearance.iconRes)
            }
            else -> {
                val icon = ResourcesCompat.getDrawable(activity.resources, appearance.iconRes, activity.theme)?.toBitmap()
                @Suppress("DEPRECATION")
                ActivityManager.TaskDescription(label, icon)
            }
        }
        activity.setTaskDescription(description)
    }
}
