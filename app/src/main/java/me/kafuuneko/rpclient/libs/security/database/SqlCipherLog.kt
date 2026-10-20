// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.database

/**
 * Turns off SQLCipher's native logging, which [net.zetetic.database.NoopTarget] does not cover:
 * SQLite error messages (they can quote SQL text and database paths) and SQLCipher's own log.
 *
 * Call it right after `System.loadLibrary("sqlcipher")` and before any database opens. It runs the
 * native part only once: shutting SQLite down again with open databases would be unsafe.
 */
object SqlCipherLog {
    /** Whether both logs were turned off in this process. */
    @Volatile
    var isSilenced = false
        private set

    private var mTried = false

    /** @return whether both logs are off; false leaves SQLCipher working as before */
    @Synchronized
    fun silence(): Boolean {
        if (mTried) return isSilenced
        mTried = true
        isSilenced = runCatching {
            System.loadLibrary("himitsu_sqlite_log")
            nativeSilence()
        }.getOrDefault(false)
        return isSilenced
    }

    @JvmStatic
    private external fun nativeSilence(): Boolean
}
