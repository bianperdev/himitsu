// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.about

/** F-Droid: links the repository; no store link, since F-Droid has no ratings. */
object FlavorAboutChannel : AboutChannel {
    override val linksRepository: Boolean = true
    override val ratesInStore: Boolean = false
}
