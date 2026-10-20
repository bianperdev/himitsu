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
 */
class AndroidKeystoreKeyWrapper(private val mAlias: String = DEFAULT_ALIAS) : KeyWrapper {

    override fun seal(plaintext: ByteArray, aad: ByteArray): ByteArray = guard {
        AesGcm.seal(existingKey() ?: generateKey(), plaintext, aad)
    }

    override fun open(box: ByteArray, aad: ByteArray): ByteArray = guard {
        val key = existingKey() ?: throw KeyWrapperException(KeyWrapperFailure.KeyLost)
        AesGcm.open(key, box, aad)
    }

    override fun resetKey() {
        guard { keyStore().deleteEntry(mAlias) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(mAlias, null) as? SecretKey

    private fun generateKey(): SecretKey {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generateKey(strongBox = true)
            } catch (_: StrongBoxUnavailableException) {
                // The device has no StrongBox; the TEE-backed key below is the normal case.
            }
        }
        return generateKey(strongBox = false)
    }

    private fun generateKey(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            mAlias,
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
