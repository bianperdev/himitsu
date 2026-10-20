// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.database

import me.kafuuneko.rpclient.libs.security.key.KeyPurpose

/**
 * The SQLCipher databases of the app, with the key purpose that encrypts each one.
 *
 * The file names are the ones RPClient used, so Room keeps its schema history.
 */
enum class EncryptedDatabase(val fileName: String, val purpose: KeyPurpose) {
    Primary("primary.sqlite", KeyPurpose.PrimaryDatabase),
    RequestLog("request_logs.sqlite", KeyPurpose.RequestLogDatabase)
}
