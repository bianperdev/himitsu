// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.key

import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom

/** Why the master key could not be loaded. None of these cases deletes any data. */
enum class VaultFailure {
    /** Encrypted data exists but the key file is gone. Only a backup of the key file can help. */
    KeyFileMissing,

    /** The key file is damaged or from an unknown format version. */
    KeyFileCorrupt,

    /** The Keystore key is gone or invalidated. The recovery code can still unlock the data. */
    KeystoreKeyLost,

    /** The Keystore failed in a way that may be temporary. Retrying may help. */
    KeystoreUnavailable,

    /** The key file could not be read or written. Retrying may help. */
    StorageError
}

/** Result of loading the master key. */
sealed class VaultLoadResult {
    /** The key is available. [created] is true when it was generated during this call. */
    class Loaded(val masterKey: MasterKey, val created: Boolean) : VaultLoadResult()

    /** The key is not available; see [failure]. */
    data class Failed(val failure: VaultFailure) : VaultLoadResult()
}

/** Result of unlocking with a recovery code. */
sealed class RecoveryResult {
    /** The key was recovered and sealed again with a new Keystore key. */
    class Recovered(val masterKey: MasterKey) : RecoveryResult()

    /** The code does not open the recovery slot. */
    data object WrongCode : RecoveryResult()

    /** No recovery code was ever configured. */
    data object NotConfigured : RecoveryResult()

    /** The key could not be recovered for another reason. */
    data class Failed(val failure: VaultFailure) : RecoveryResult()
}

/**
 * Creates, loads and recovers the master key.
 *
 * Core rules:
 * - Every new Keystore slot uses a new Keystore key ID and no key is ever deleted, so a key
 *   file set aside by "start over" stays openable by its own Keystore key.
 * - A new key is created only when there is no key file *and* the caller confirms that no
 *   encrypted data exists. Otherwise a missing key file is reported, never papered over with a
 *   new key that would make the existing data unreadable.
 * - Every failure is returned as a [VaultFailure]; this class never deletes data.
 * - Key material never appears in exceptions, results' `toString` or logs.
 */
class MasterKeyVault(
    private val mStore: MasterKeyFileStore,
    private val mKeyWrapper: KeyWrapper,
    private val mRandom: SecureRandom
) {

    /**
     * Loads the master key, creating it on a fresh install.
     *
     * @param hasEncryptedData whether encrypted databases or files already exist; when true a
     *   missing key file is an error instead of a fresh install
     */
    fun loadOrCreate(hasEncryptedData: Boolean): VaultLoadResult {
        if (!mStore.exists()) {
            if (hasEncryptedData) return VaultLoadResult.Failed(VaultFailure.KeyFileMissing)
            return create()
        }
        // Read and open the Keystore slot
        val file = when (val read = readFile()) {
            is FileRead.Ok -> read.file
            is FileRead.Error -> return VaultLoadResult.Failed(read.failure)
        }
        val slot = file.slot(KeySlotType.Keystore)
            ?: return VaultLoadResult.Failed(VaultFailure.KeystoreKeyLost)
        val bytes = try {
            mKeyWrapper.open(slot.keystoreKeyId(), slot.sealedKey, MasterKeyFileCodec.slotAad(KeySlotType.Keystore))
        } catch (e: KeyWrapperException) {
            return VaultLoadResult.Failed(e.failure.toVaultFailure())
        }
        val masterKey = adoptOrCorrupt(bytes) ?: return VaultLoadResult.Failed(VaultFailure.KeyFileCorrupt)
        return VaultLoadResult.Loaded(masterKey, created = false)
    }

    /**
     * Unlocks with the recovery code after the Keystore key was lost, then seals the master key
     * again with a new Keystore key so the next start needs no code.
     */
    fun recover(code: RecoveryCode): RecoveryResult {
        // Open the recovery slot
        val file = when (val read = readFile()) {
            is FileRead.Ok -> read.file
            is FileRead.Error -> return RecoveryResult.Failed(read.failure)
        }
        val slot = file.slot(KeySlotType.RecoveryCode) ?: return RecoveryResult.NotConfigured
        val kek = recoveryKek(code, slot.salt)
        val bytes = try {
            AesGcm.open(kek, slot.sealedKey, MasterKeyFileCodec.slotAad(KeySlotType.RecoveryCode))
        } catch (_: GeneralSecurityException) {
            return RecoveryResult.WrongCode
        } finally {
            kek.fill(0)
        }
        val masterKey = adoptOrCorrupt(bytes) ?: return RecoveryResult.Failed(VaultFailure.KeyFileCorrupt)
        // Replace the lost Keystore slot with one under a new key, keeping the recovery slot
        return try {
            mStore.write(file.withSlot(keystoreSlot(masterKey)))
            RecoveryResult.Recovered(masterKey)
        } catch (e: KeyWrapperException) {
            masterKey.destroy()
            RecoveryResult.Failed(e.failure.toVaultFailure())
        } catch (_: IOException) {
            masterKey.destroy()
            RecoveryResult.Failed(VaultFailure.StorageError)
        }
    }

    /** Whether a recovery code is configured. */
    fun hasRecoveryCode(): Boolean = (readFile() as? FileRead.Ok)?.file?.slot(KeySlotType.RecoveryCode) != null

    /**
     * Adds a recovery slot for [code], replacing any previous code.
     *
     * @throws IOException when the key file cannot be updated
     * @throws MasterKeyFileFormatException when the key file is damaged
     */
    fun setRecoveryCode(masterKey: MasterKey, code: RecoveryCode) {
        val salt = ByteArray(SALT_SIZE).also(mRandom::nextBytes)
        val kek = recoveryKek(code, salt)
        val raw = masterKey.copyBytes()
        try {
            val sealed = AesGcm.seal(kek, raw, MasterKeyFileCodec.slotAad(KeySlotType.RecoveryCode), mRandom)
            mStore.write(mStore.read().withSlot(KeySlot(KeySlotType.RecoveryCode, salt, sealed)))
        } finally {
            kek.fill(0)
            raw.fill(0)
        }
    }

    /**
     * Removes the recovery slot. Afterwards a lost Keystore key means the data is lost.
     *
     * @throws IOException when the key file cannot be updated
     * @throws MasterKeyFileFormatException when the key file is damaged
     */
    fun removeRecoveryCode() {
        mStore.write(mStore.read().withoutSlot(KeySlotType.RecoveryCode))
    }

    /**
     * Moves the key file aside so a new key can be created, after the user chose to start over.
     *
     * @throws IOException when the file cannot be renamed
     */
    fun moveKeyFileAside(suffix: String) {
        mStore.moveAside(suffix)
    }

    private fun create(): VaultLoadResult {
        val masterKey = MasterKey.generate(mRandom)
        return try {
            // A new key ID, so any key file set aside keeps a working Keystore key
            mStore.write(MasterKeyFile(listOf(keystoreSlot(masterKey))))
            VaultLoadResult.Loaded(masterKey, created = true)
        } catch (e: KeyWrapperException) {
            masterKey.destroy()
            VaultLoadResult.Failed(e.failure.toVaultFailure())
        } catch (_: IOException) {
            masterKey.destroy()
            VaultLoadResult.Failed(VaultFailure.StorageError)
        }
    }

    /** Seals [masterKey] under a newly generated Keystore key ID, stored in the slot's salt field. */
    private fun keystoreSlot(masterKey: MasterKey): KeySlot {
        val keyId = ByteArray(KEY_ID_SIZE).also(mRandom::nextBytes).joinToString("") { "%02x".format(it) }
        val raw = masterKey.copyBytes()
        try {
            val sealed = mKeyWrapper.seal(keyId, raw, MasterKeyFileCodec.slotAad(KeySlotType.Keystore))
            return KeySlot(KeySlotType.Keystore, keyId.toByteArray(Charsets.US_ASCII), sealed)
        } finally {
            raw.fill(0)
        }
    }

    /** Key ID of a Keystore slot; empty for slots written before key IDs existed. */
    private fun KeySlot.keystoreKeyId(): String = String(salt, Charsets.US_ASCII)

    private fun recoveryKek(code: RecoveryCode, salt: ByteArray): ByteArray {
        val material = code.keyMaterial()
        try {
            return Hkdf.deriveSha256(material, salt, RECOVERY_INFO, AesGcm.KEY_SIZE_BYTES)
        } finally {
            material.fill(0)
        }
    }

    private fun adoptOrCorrupt(bytes: ByteArray): MasterKey? {
        if (bytes.size != MasterKey.SIZE_BYTES) {
            bytes.fill(0)
            return null
        }
        return MasterKey.adopt(bytes)
    }

    private fun readFile(): FileRead = try {
        FileRead.Ok(mStore.read())
    } catch (_: MasterKeyFileFormatException) {
        FileRead.Error(VaultFailure.KeyFileCorrupt)
    } catch (_: IOException) {
        FileRead.Error(VaultFailure.StorageError)
    }

    private fun KeyWrapperFailure.toVaultFailure(): VaultFailure = when (this) {
        KeyWrapperFailure.KeyLost -> VaultFailure.KeystoreKeyLost
        KeyWrapperFailure.Unavailable -> VaultFailure.KeystoreUnavailable
    }

    private sealed class FileRead {
        class Ok(val file: MasterKeyFile) : FileRead()
        class Error(val failure: VaultFailure) : FileRead()
    }

    private companion object {
        const val SALT_SIZE = 16
        const val KEY_ID_SIZE = 8
        val RECOVERY_INFO = "himitsu/v1/recovery-slot".toByteArray(Charsets.UTF_8)
    }
}
