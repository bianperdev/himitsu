// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.database

import me.kafuuneko.rpclient.libs.security.key.KeyPurpose

/**
 * The SQLCipher databases of the app, with the key purpose that encrypts each one.
 *
 * The file names are the ones RPClient used, so Room keeps its schema history.
 *
 * @property movedFromNoBackup earlier builds kept this database in `noBackupFilesDir`; it moves to
 *   the databases directory once, before migration (backups exclude every directory anyway)
 */
enum class EncryptedDatabase(val fileName: String, val purpose: KeyPurpose, val movedFromNoBackup: Boolean = false) {
    Primary("primary.sqlite", KeyPurpose.PrimaryDatabase),
    RequestLog("request_logs.sqlite", KeyPurpose.RequestLogDatabase),

    /** Registry of on-device models (names and metadata); the model files stay plain. */
    LocalModels("local_models.sqlite", KeyPurpose.LocalModelDatabase, movedFromNoBackup = true)
}
