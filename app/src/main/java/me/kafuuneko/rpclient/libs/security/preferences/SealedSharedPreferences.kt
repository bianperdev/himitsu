// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.preferences

import android.content.SharedPreferences
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import me.kafuuneko.rpclient.libs.security.key.AesGcm

/** Result of reading one stored string. */
internal sealed class OpenedValue {
    /** A value written before encryption existed; returned as is and sealed at startup. */
    data class Plain(val value: String) : OpenedValue()

    /** A sealed value, decrypted. */
    data class Value(val value: String) : OpenedValue()

    /** Sealed, but the key is not available yet (startup) or not at all (Keystore failure). */
    data object Unavailable : OpenedValue()

    /** Sealed with another key, for example before the user started over. */
    data object Unreadable : OpenedValue()
}

/**
 * Seals preference strings with AES-256-GCM (P-05).
 *
 * A sealed value is `hm1:` followed by Base64 of `IV || ciphertext || tag`. The preference
 * name is the associated data, so a sealed value cannot be moved to another preference.
 *
 * @param mKeySource returns the current preferences key, or null when it is not available;
 *   the returned array is wiped after each use
 */
internal class PreferenceCipher(
    private val mKeySource: () -> ByteArray?,
    private val mRandom: SecureRandom = SecureRandom()
) {
    /** Seals [value] for [name], or returns null when the key is not available. */
    fun seal(name: String, value: String): String? {
        val key = mKeySource() ?: return null
        try {
            val box = AesGcm.seal(key, value.toByteArray(Charsets.UTF_8), aad(name), mRandom)
            return PREFIX + Base64.getEncoder().encodeToString(box)
        } finally {
            key.fill(0)
        }
    }

    /** Reads a stored string for [name]. */
    fun open(name: String, stored: String): OpenedValue {
        if (!isSealed(stored)) return OpenedValue.Plain(stored)
        val key = mKeySource() ?: return OpenedValue.Unavailable
        return try {
            val box = Base64.getDecoder().decode(stored.substring(PREFIX.length))
            OpenedValue.Value(String(AesGcm.open(key, box, aad(name)), Charsets.UTF_8))
        } catch (_: GeneralSecurityException) {
            OpenedValue.Unreadable
        } catch (_: IllegalArgumentException) {
            OpenedValue.Unreadable
        } finally {
            key.fill(0)
        }
    }

    private fun aad(name: String): ByteArray = (AAD_PREFIX + name).toByteArray(Charsets.UTF_8)

    companion object {
        const val PREFIX = "hm1:"
        private const val AAD_PREFIX = "himitsu/v1/preference/"

        fun isSealed(stored: String): Boolean = stored.startsWith(PREFIX)
    }
}

/**
 * [SharedPreferences] whose strings and string sets are stored sealed (P-05).
 *
 * - Numbers and booleans pass through: they are switches and sizes, not personal text.
 * - Strings named in `mPlainKeys` pass through too: settings such as the theme, which are read
 *   by the first frame and must not wait for the Keystore.
 * - While the key is unavailable, sealed values read as their defaults and writes of strings
 *   are dropped instead of being stored in plain text. This only happens behind the gate
 *   screen, where the user cannot edit anything.
 */
internal class SealedSharedPreferences(
    private val mDelegate: SharedPreferences,
    private val mCipher: PreferenceCipher,
    private val mPlainKeys: Set<String> = emptySet()
) : SharedPreferences {

    override fun getString(key: String, defValue: String?): String? {
        val stored = mDelegate.getString(key, null) ?: return defValue
        // Plain settings are returned as is; one sealed by an earlier build is still opened
        if (key in mPlainKeys && !PreferenceCipher.isSealed(stored)) return stored
        return when (val opened = mCipher.open(key, stored)) {
            is OpenedValue.Plain -> opened.value
            is OpenedValue.Value -> opened.value
            OpenedValue.Unavailable, OpenedValue.Unreadable -> defValue
        }
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        val stored = mDelegate.getStringSet(key, null) ?: return defValues
        val opened = stored.map { mCipher.open(key, it) }
        if (opened.any { it == OpenedValue.Unavailable || it == OpenedValue.Unreadable }) return defValues
        return opened.mapTo(mutableSetOf()) {
            when (it) {
                is OpenedValue.Plain -> it.value
                is OpenedValue.Value -> it.value
                else -> error("Unreachable")
            }
        }
    }

    override fun getAll(): MutableMap<String, *> {
        val result = mutableMapOf<String, Any?>()
        mDelegate.all.forEach { (key, value) ->
            when (value) {
                is String -> getString(key, null)?.let { result[key] = it }
                is Set<*> -> getStringSet(key, null)?.let { result[key] = it }
                else -> result[key] = value
            }
        }
        return result
    }

    override fun getInt(key: String, defValue: Int): Int = mDelegate.getInt(key, defValue)
    override fun getLong(key: String, defValue: Long): Long = mDelegate.getLong(key, defValue)
    override fun getFloat(key: String, defValue: Float): Float = mDelegate.getFloat(key, defValue)
    override fun getBoolean(key: String, defValue: Boolean): Boolean = mDelegate.getBoolean(key, defValue)
    override fun contains(key: String): Boolean = mDelegate.contains(key)
    override fun edit(): SharedPreferences.Editor = SealedEditor(mDelegate.edit())

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        mDelegate.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        mDelegate.unregisterOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Seals every string still stored in plain form, written by a build without encryption.
     *
     * @return the number of preferences sealed; 0 when the key is unavailable
     */
    fun sealPlainValues(): Int {
        val editor = mDelegate.edit()
        var count = 0
        // Seal each plain string or string set; stop if the key disappears midway
        for ((key, value) in mDelegate.all) {
            when {
                key in mPlainKeys -> Unit
                value is String && !PreferenceCipher.isSealed(value) -> {
                    editor.putString(key, mCipher.seal(key, value) ?: return 0)
                    count++
                }
                value is Set<*> && value.any { it is String && !PreferenceCipher.isSealed(it) } -> {
                    val sealed = value.filterIsInstance<String>().map { item ->
                        if (PreferenceCipher.isSealed(item)) item else mCipher.seal(key, item) ?: return 0
                    }
                    editor.putStringSet(key, sealed.toSet())
                    count++
                }
            }
        }
        if (count > 0) editor.commit()
        return count
    }

    /** Whether any value is sealed, so a missing key file must not be replaced by a new key. */
    fun hasSealedValues(): Boolean = mDelegate.all.values.any { value ->
        (value is String && PreferenceCipher.isSealed(value)) ||
            (value is Set<*> && value.any { it is String && PreferenceCipher.isSealed(it) })
    }

    private inner class SealedEditor(private val mEditor: SharedPreferences.Editor) : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            if (value == null) {
                mEditor.remove(key)
                return this
            }
            if (key in mPlainKeys) {
                mEditor.putString(key, value)
                return this
            }
            mCipher.seal(key, value)?.let { mEditor.putString(key, it) }
            return this
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
            if (values == null) {
                mEditor.remove(key)
                return this
            }
            val sealed = values.map { mCipher.seal(key, it) ?: return this }
            mEditor.putStringSet(key, sealed.toMutableSet())
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            mEditor.putInt(key, value)
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            mEditor.putLong(key, value)
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            mEditor.putFloat(key, value)
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            mEditor.putBoolean(key, value)
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            mEditor.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            mEditor.clear()
            return this
        }

        override fun commit(): Boolean = mEditor.commit()

        override fun apply() {
            mEditor.apply()
        }
    }
}
