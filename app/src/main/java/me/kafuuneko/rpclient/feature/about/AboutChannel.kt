// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.about

import android.app.Activity

/**
 * What the About screen offers in this distribution channel (see "Distribution flavors" in
 * ROADMAP.md). Each flavor provides one `FlavorAboutChannel` object in its own source set.
 */
interface AboutChannel {
    /**
     * Whether the repository and issue tracker are linked. False in `play`: the repository may
     * hold donation links, which Google Play forbids even indirectly.
     */
    val linksRepository: Boolean

    /** Whether the "rate the app" card is shown; only the store build has one. */
    val ratesInStore: Boolean

    /** Opens the store page to rate the app. Does nothing where [ratesInStore] is false. */
    fun openStore(activity: Activity) = Unit
}
