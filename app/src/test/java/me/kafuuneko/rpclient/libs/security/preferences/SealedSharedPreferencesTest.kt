// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.preferences

import android.content.SharedPreferences
import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses placeholder persona text only. */
class SealedSharedPreferencesTest {
    private val random = SecureRandom()
    private val key = ByteArray(32).also(random::nextBytes)
    private var keyAvailable = true
    private val backing = MapPreferences()
    private val prefs = SealedSharedPreferences(backing, PreferenceCipher({ if (keyAvailable) key.copyOf() else null }, random))

    @Test
    fun stringsAreStoredSealedAndReadBack() {
        prefs.edit().putString("userDescription", "placeholder persona").putInt("count", 3).commit()

        val stored = backing.values["userDescription"] as String
        assertTrue(stored.startsWith(PreferenceCipher.PREFIX))
        assertFalse(stored.contains("placeholder"))
        assertEquals("placeholder persona", prefs.getString("userDescription", null))
        assertEquals(3, prefs.getInt("count", 0))
    }

    @Test
    fun aSealedValueCannotBeMovedToAnotherPreference() {
        prefs.edit().putString("mainPrompt", "placeholder prompt").commit()
        backing.values["userName"] = backing.values["mainPrompt"]

        assertEquals("fallback", prefs.getString("userName", "fallback"))
    }

    @Test
    fun withoutTheKeyReadsGiveDefaultsAndWritesAreDroppedNotStoredInPlain() {
        prefs.edit().putString("userName", "placeholder name").commit()
        keyAvailable = false

        prefs.edit().putString("userName", "placeholder other").commit()

        assertEquals("fallback", prefs.getString("userName", "fallback"))
        assertFalse((backing.values["userName"] as String).contains("placeholder"))
        keyAvailable = true
        assertEquals("placeholder name", prefs.getString("userName", null))
    }

    @Test
    fun plainValuesFromEarlierBuildsAreReadAndThenSealed() {
        backing.values["userName"] = "placeholder legacy"
        backing.values["tags"] = mutableSetOf("placeholder tag")

        assertEquals("placeholder legacy", prefs.getString("userName", null))
        assertEquals(2, prefs.sealPlainValues())
        assertTrue(prefs.hasSealedValues())
        assertFalse((backing.values["userName"] as String).contains("placeholder"))
        assertEquals(setOf("placeholder tag"), prefs.getStringSet("tags", null))
        assertEquals(0, prefs.sealPlainValues())
    }

    @Test
    fun nullStringRemovesTheValue() {
        prefs.edit().putString("userName", "placeholder").commit()
        prefs.edit().putString("userName", null).commit()

        assertNull(prefs.getString("userName", null))
    }

    /** Minimal map-backed [SharedPreferences] for JVM tests. */
    private class MapPreferences : SharedPreferences {
        val values = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            (values[key] as? Set<String>)?.toMutableSet() ?: defValues

        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = key in values
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String, value: String?) = also { pending[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = also { pending[key] = values }
            override fun putInt(key: String, value: Int) = also { pending[key] = value }
            override fun putLong(key: String, value: Long) = also { pending[key] = value }
            override fun putFloat(key: String, value: Float) = also { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = also { pending[key] = value }
            override fun remove(key: String) = also { removed += key }
            override fun clear() = also { clear = true }

            override fun commit(): Boolean {
                if (clear) values.clear()
                removed.forEach(values::remove)
                pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                return true
            }

            override fun apply() {
                commit()
            }
        }
    }
}
