// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.key

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * [KeyWrapper] backed by an AES-256-GCM key in the Android Keystore.
 *
 * - The key is not bound to user authentication: enrolling a fingerprint or removing the
 *   screen lock never invalidates it, so the app lock can never cause data loss.
 * - StrongBox is used when the device has it, with a fallback to the TEE.
 * - The key cannot be exported and is deleted by the system when the app is uninstalled.
 * - Each key ID maps to its own alias; the empty ID is the alias used by the first builds.
 *
 * @param mAliasPrefix alias of the empty key ID and prefix of the others; tests use their own
 */
class AndroidKeystoreKeyWrapper(private val mAliasPrefix: String = DEFAULT_ALIAS) : KeyWrapper {

    override fun seal(keyId: String, plaintext: ByteArray, aad: ByteArray): ByteArray = guard {
        val alias = alias(keyId)
        AesGcm.seal(existingKey(alias) ?: generateKey(alias), plaintext, aad)
    }

    override fun open(keyId: String, box: ByteArray, aad: ByteArray): ByteArray = guard {
        val key = existingKey(alias(keyId)) ?: throw KeyWrapperException(KeyWrapperFailure.KeyLost)
        AesGcm.open(key, box, aad)
    }

    /** Deletes the key [keyId]. Only for tests that clean up their own keys. */
    fun deleteKey(keyId: String) {
        guard { keyStore().deleteEntry(alias(keyId)) }
    }

    private fun alias(keyId: String): String = if (keyId.isEmpty()) mAliasPrefix else "${mAliasPrefix}_$keyId"

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun existingKey(alias: String): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    private fun generateKey(alias: String): SecretKey {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generateKey(alias, strongBox = true)
            } catch (_: StrongBoxUnavailableException) {
                // The device has no StrongBox; the TEE-backed key below is the normal case.
            }
        }
        return generateKey(alias, strongBox = false)
    }

    private fun generateKey(alias: String, strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .apply {
                if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true)
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }

    /** Maps key store exceptions to [KeyWrapperException] without exposing any key material. */
    private inline fun <T> guard(block: () -> T): T {
        try {
            return block()
        } catch (e: KeyWrapperException) {
            throw e
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw KeyWrapperException(KeyWrapperFailure.KeyLost, e)
        } catch (e: UnrecoverableKeyException) {
            throw KeyWrapperException(KeyWrapperFailure.KeyLost, e)
        } catch (e: AEADBadTagException) {
            // The box was sealed with another key, for example one deleted and recreated.
            throw KeyWrapperException(KeyWrapperFailure.KeyLost, e)
        } catch (e: Exception) {
            throw KeyWrapperException(KeyWrapperFailure.Unavailable, e)
        }
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "himitsu_master_key_wrap"
    }
}
