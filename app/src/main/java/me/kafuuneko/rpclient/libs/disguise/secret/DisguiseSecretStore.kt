// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise.secret

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.disguise.Appearance

/**
 * Stores and checks the disguise shortcut.
 *
 * Responsibilities:
 * - keep only a [SecretRecord] and the [AttemptState], in a preferences file of their own
 * - derive keys off the main thread, one at a time
 * - wipe every shortcut passed in once it has been used
 *
 * The record is deliberately outside the encrypted database and the sealed preferences: both
 * need the master key, and the decoy must keep working, and lead to the recovery screen, even
 * when the Keystore fails. Backups exclude every app file already.
 */
class DisguiseSecretStore(
    context: Context,
    private val mRandom: SecureRandom = SecureRandom(),
    private val mClock: () -> Long = System::currentTimeMillis
) {
    private val mPrefs: SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val mMutex = Mutex()

    /** Whether a shortcut is set for [appearance]. */
    fun isSet(appearance: Appearance): Boolean = readRecord()?.appearanceId == appearance.id

    /** Replaces the shortcut with [secret] for [appearance] and resets the attempts. Wipes [secret]. */
    suspend fun set(appearance: Appearance, secret: CharArray) = withContext(Dispatchers.Default) {
        mMutex.withLock {
            try {
                val record = SecretRecord.create(appearance, secret, mRandom)
                mPrefs.edit(commit = true) {
                    putInt(KEY_VERSION, FORMAT_VERSION)
                    putString(KEY_APPEARANCE, record.appearanceId)
                    putInt(KEY_ITERATIONS, record.iterations)
                    putString(KEY_SALT, ENCODER.encodeToString(record.salt))
                    putString(KEY_HASH, ENCODER.encodeToString(record.hash))
                    putInt(KEY_FAILURES, 0)
                    putLong(KEY_BLOCKED_UNTIL, 0L)
                }
            } finally {
                secret.fill('\u0000')
            }
        }
    }

    /**
     * Checks a shortcut attempt. Ignored, and false, while attempts are blocked. Wipes [candidate].
     *
     * @return true only when [candidate] is the shortcut of [appearance]
     */
    suspend fun verify(appearance: Appearance, candidate: CharArray): Boolean = withContext(Dispatchers.Default) {
        mMutex.withLock {
            try {
                val record = readRecord() ?: return@withLock false
                val now = mClock()
                val attempts = AttemptState(mPrefs.getInt(KEY_FAILURES, 0), mPrefs.getLong(KEY_BLOCKED_UNTIL, 0L))
                if (AttemptLimiter.isBlocked(attempts, now)) return@withLock false
                val matches = record.matches(appearance, candidate)
                val next = if (matches) AttemptState() else AttemptLimiter.afterFailure(attempts, now)
                mPrefs.edit(commit = true) {
                    putInt(KEY_FAILURES, next.failures)
                    putLong(KEY_BLOCKED_UNTIL, next.blockedUntil)
                }
                matches
            } finally {
                candidate.fill('\u0000')
            }
        }
    }

    /** Removes the shortcut, for example when disguise is turned off. */
    fun clear() {
        mPrefs.edit(commit = true) { clear() }
    }

    private fun readRecord(): SecretRecord? {
        if (mPrefs.getInt(KEY_VERSION, 0) != FORMAT_VERSION) return null
        val appearanceId = mPrefs.getString(KEY_APPEARANCE, null) ?: return null
        val salt = mPrefs.getString(KEY_SALT, null) ?: return null
        val hash = mPrefs.getString(KEY_HASH, null) ?: return null
        return SecretRecord(appearanceId, mPrefs.getInt(KEY_ITERATIONS, 0), DECODER.decode(salt), DECODER.decode(hash))
    }

    private companion object {
        const val FILE_NAME = "himitsu_disguise_secret"
        const val FORMAT_VERSION = 1
        const val KEY_VERSION = "version"
        const val KEY_APPEARANCE = "appearance"
        const val KEY_ITERATIONS = "iterations"
        const val KEY_SALT = "salt"
        const val KEY_HASH = "hash"
        const val KEY_FAILURES = "failures"
        const val KEY_BLOCKED_UNTIL = "blocked_until"
        val ENCODER: Base64.Encoder = Base64.getEncoder()
        val DECODER: Base64.Decoder = Base64.getDecoder()
    }
}
