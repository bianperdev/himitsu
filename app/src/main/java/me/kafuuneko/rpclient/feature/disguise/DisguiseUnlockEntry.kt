// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise

import android.app.Activity
import android.content.Context
import android.content.Intent
import me.kafuuneko.rpclient.libs.disguise.AppearanceSwitcher
import me.kafuuneko.rpclient.libs.security.UnlockEntry

/**
 * [UnlockEntry] that shows the decoy instead of the Himitsu gate screen while disguise is on.
 *
 * `CoreActivity` calls it when a page must be hidden because the app locked. With disguise
 * off it delegates to [mGateScreen] unchanged. With disguise on it opens the decoy over the
 * page ("covering" mode); the decoy then authenticates through its shortcut, so the lock is
 * never skipped and a casual observer only ever sees the decoy.
 */
class DisguiseUnlockEntry(
    private val mAppearanceSwitcher: AppearanceSwitcher,
    private val mGateScreen: UnlockEntry
) : UnlockEntry {
    override fun open(context: Context) {
        if (!mAppearanceSwitcher.current.isDecoy) {
            mGateScreen.open(context)
            return
        }
        val intent = Intent(context, DisguiseActivity::class.java)
            .putExtra(DisguiseActivity.EXTRA_COVERING, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
