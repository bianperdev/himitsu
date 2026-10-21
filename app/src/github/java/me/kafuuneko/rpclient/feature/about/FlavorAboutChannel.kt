// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.about

/** GitHub Releases: links the repository; no Google Play link. */
object FlavorAboutChannel : AboutChannel {
    override val repositoryUrl: String = "https://github.com/bianperdev/himitsu"
    override val upstreamUrl: String = "https://github.com/KafuuNeko/RPClient"
    override val linksExternalProjects: Boolean = true
    override val ratesInStore: Boolean = false
}
