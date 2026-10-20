// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import java.io.File
import java.security.SecureRandom
import me.kafuuneko.rpclient.feature.lock.LockActivityEntry
import me.kafuuneko.rpclient.libs.security.database.PlaintextDatabaseMigrator
import me.kafuuneko.rpclient.libs.security.files.FileCipherSource
import me.kafuuneko.rpclient.libs.security.key.AndroidKeystoreKeyWrapper
import me.kafuuneko.rpclient.libs.security.key.MasterKeyFileStore
import me.kafuuneko.rpclient.libs.security.key.MasterKeyVault
import me.kafuuneko.rpclient.libs.security.lock.AppLockManager
import me.kafuuneko.rpclient.libs.security.storage.MASTER_KEY_FILE_NAME
import me.kafuuneko.rpclient.libs.security.storage.ProtectedFiles
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Koin module of the `feat/security` track. */
internal val securityModule = module {
    single {
        MasterKeyVault(
            mStore = MasterKeyFileStore(File(androidContext().noBackupFilesDir, MASTER_KEY_FILE_NAME)),
            mKeyWrapper = AndroidKeystoreKeyWrapper(),
            mRandom = SecureRandom()
        )
    }
    single { SecureStorageManager(androidContext(), get(), PlaintextDatabaseMigrator()) }
    single<FileCipherSource> { get<SecureStorageManager>() }
    single { AppLockManager(androidContext()) }
    single { AppGate(get(), get()) }
    single<UnlockEntry> { LockActivityEntry }
    single { RequestLogGuard(get()) }
    single { ProtectedFiles(androidContext()) }
}
