// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.about

/** F-Droid: links the repository; no store link, since F-Droid has no ratings. */
object FlavorAboutChannel : AboutChannel {
    override val repositoryUrl: String = "https://github.com/bianperdev/himitsu"
    override val upstreamUrl: String = "https://github.com/KafuuNeko/RPClient"
    override val ratesInStore: Boolean = false
}
