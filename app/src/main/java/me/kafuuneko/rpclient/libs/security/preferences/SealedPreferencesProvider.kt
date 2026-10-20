// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.preferences

import android.content.Context
import android.content.SharedPreferences
import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.PreferencesProvider
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Kotpref provider that stores strings sealed (P-05). `AppModel` uses it; `SecurityPrefs`
 * does not, because the lock switches must be readable before any key is loaded.
 *
 * [keySource] is installed at startup and returns the preferences key derived from the master
 * key, loading the master key on first use.
 */
object SealedPreferencesProvider : PreferencesProvider {
    @Volatile
    var keySource: () -> ByteArray? = { null }

    private val mCipher = PreferenceCipher({ keySource() })
    private val mInstances = ConcurrentHashMap<String, SealedSharedPreferences>()

    /**
     * Non-sensitive settings kept plain per preference file, so the first frame (theme) and
     * image settings never wait for the Keystore. Personal text is never listed here.
     */
    private val mPlainKeys = mapOf("AppModel" to setOf("themeMode", "imageSendSettings"))

    override fun get(context: Context, name: String, mode: Int): SharedPreferences =
        mInstances.getOrPut(name) {
            SealedSharedPreferences(context.getSharedPreferences(name, mode), mCipher, mPlainKeys[name].orEmpty())
        }

    /**
     * Seals the plain strings of [model] when it uses this provider.
     *
     * Kotpref wraps the provider's object in its own class, so the sealed instance is found by
     * the model's preference name rather than through `model.preferences`.
     *
     * @return the number of preferences sealed
     */
    fun sealPlainValues(model: KotprefModel): Int {
        model.preferences // Makes Kotpref ask this provider for the instance
        return mInstances[model.kotprefName]?.sealPlainValues() ?: 0
    }

    /**
     * Whether any preference file holds sealed values. Reads the files directly, because the
     * preference objects may not exist yet this early in startup.
     */
    fun hasSealedFiles(context: Context): Boolean = sharedPrefsDir(context).listFiles().orEmpty().any(::hasSealedValues)

    /** Whether [file] is a preference file that holds sealed values. */
    fun hasSealedValues(file: File): Boolean =
        file.isFile && file.name.endsWith(".xml") && runCatching { file.readText().contains(">" + PreferenceCipher.PREFIX) }
            .getOrDefault(false)

    /**
     * Copies every file with sealed values to `<name>.xml<suffix>` and empties it, after the
     * user chose to start over. Nothing is deleted: the copies keep the old sealed values.
     */
    fun setAside(context: Context, suffix: String) {
        sharedPrefsDir(context).listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".xml") && it.readText().contains(">" + PreferenceCipher.PREFIX) }
            .forEach { file ->
                file.copyTo(File(file.path + suffix), overwrite = true)
                val instance = mInstances[file.name.removeSuffix(".xml")]
                if (instance != null) {
                    instance.edit().clear().commit()
                } else {
                    file.delete()
                }
            }
    }

    private fun sharedPrefsDir(context: Context): File = File(context.applicationInfo.dataDir, "shared_prefs")
}
