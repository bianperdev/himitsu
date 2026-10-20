// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import java.net.URI

/**
 * Detects endpoints reached without TLS (P-09).
 *
 * Clear-text HTTP stays allowed because self-hosted servers on a local network often have no
 * certificate, but the user is warned: anyone on the same network can read the prompts and the
 * API key. Loopback addresses never leave the device and are not reported.
 */
object CleartextPolicy {
    private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1", "[::1]")

    /** Whether [baseUrl] uses plain HTTP to a host other than this device. */
    fun isUnencryptedRemote(baseUrl: String): Boolean {
        val uri = runCatching { URI(baseUrl.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("http", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return true
        return host !in LOOPBACK_HOSTS && !host.startsWith("127.")
    }
}
