// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.about

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri

/**
 * Google Play: the "rate the app" card, and no repository links, because the repository may hold
 * donation links, which Google Play forbids even indirectly.
 */
object FlavorAboutChannel : AboutChannel {
    override val repositoryUrl: String? = null
    override val upstreamUrl: String? = null
    override val linksExternalProjects: Boolean = false
    override val ratesInStore: Boolean = true

    /** Opens the Play Store app, then any store app, then the web page. */
    override fun openStore(activity: Activity) {
        val page = "market://details?id=${activity.packageName}".toUri()
        val attempts = listOf(
            Intent(Intent.ACTION_VIEW, page).setPackage("com.android.vending"),
            Intent(Intent.ACTION_VIEW, page),
            Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=${activity.packageName}".toUri())
        )
        for (intent in attempts) {
            try {
                activity.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next way to reach the store
            }
        }
    }
}
