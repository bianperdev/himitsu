// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.about

/** GitHub Releases: links the repository; no Google Play link. */
object FlavorAboutChannel : AboutChannel {
    override val linksRepository: Boolean = true
    override val ratesInStore: Boolean = false
}
