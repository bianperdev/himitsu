// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Supplies SQLCipher passphrases once the encrypted storage is ready. */
fun interface DatabaseKeySource {
    /**
     * Returns the raw-key passphrase of [database], blocking until the master key is loaded.
     *
     * Only called from Room's background threads; never from the main thread.
     */
    fun awaitPassphrase(database: EncryptedDatabase): ByteArray
}

/**
 * Room open helper factory that defers SQLCipher keying until the database is first opened.
 *
 * - Room creates its open helper as soon as the `AppDatabase` singleton is built, which can
 *   happen on the main thread before the master key is loaded. The real SQLCipher helper is
 *   only created on the first query, which Room always runs on a background thread.
 * - While the key is not available (startup in progress, or a Keystore error the user has not
 *   resolved yet) that first query waits instead of failing, so no screen crashes and no
 *   empty database is ever created over the encrypted one.
 */
class KeyedOpenHelperFactory(
    private val mDatabase: EncryptedDatabase,
    private val mKeySource: DatabaseKeySource
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        DeferredOpenHelper(configuration)

    private inner class DeferredOpenHelper(
        private val mConfiguration: SupportSQLiteOpenHelper.Configuration
    ) : SupportSQLiteOpenHelper {
        private var mDelegate: SupportSQLiteOpenHelper? = null
        private var mWriteAheadLogging: Boolean? = null

        override val databaseName: String?
            get() = mConfiguration.name

        override val writableDatabase: SupportSQLiteDatabase
            get() = delegate().writableDatabase

        override val readableDatabase: SupportSQLiteDatabase
            get() = delegate().readableDatabase

        @Synchronized
        override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
            mWriteAheadLogging = enabled
            mDelegate?.setWriteAheadLoggingEnabled(enabled)
        }

        @Synchronized
        override fun close() {
            mDelegate?.close()
        }

        @Synchronized
        private fun delegate(): SupportSQLiteOpenHelper {
            mDelegate?.let { return it }
            // SupportOpenHelperFactory keeps the passphrase to reopen the database later
            val passphrase = mKeySource.awaitPassphrase(mDatabase)
            val helper = SupportOpenHelperFactory(passphrase).create(mConfiguration)
            mWriteAheadLogging?.let(helper::setWriteAheadLoggingEnabled)
            mDelegate = helper
            return helper
        }
    }
}
