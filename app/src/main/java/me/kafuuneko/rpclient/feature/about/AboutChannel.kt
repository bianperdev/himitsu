// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.about

import android.app.Activity

/**
 * What the About screen offers in this distribution channel (see "Distribution flavors" in
 * ROADMAP.md). Each flavor provides one `FlavorAboutChannel` object in its own source set.
 */
interface AboutChannel {
    /**
     * Himitsu's repository, also used for the issue tracker; null in `play`, whose APK must not
     * contain it: the repository may hold donation links, which Google Play forbids even indirectly.
     */
    val repositoryUrl: String?

    /** RPClient's repository, for the credit; null in `play` for the same reason. */
    val upstreamUrl: String?

    /** Whether the "rate the app" card is shown; only the store build has one. */
    val ratesInStore: Boolean

    /** Opens the store page to rate the app. Does nothing where [ratesInStore] is false. */
    fun openStore(activity: Activity) = Unit
}
