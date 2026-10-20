// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.key

import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Vault logic with an in-memory [KeyWrapper] instead of the Android Keystore.
 *
 * Keys are compared through derived keys and `contentEquals`, so no key bytes are ever printed.
 */
class MasterKeyVaultTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val random = SecureRandom()
    private val wrapper = FakeKeyWrapper(random)
    private val keyFile by lazy { File(folder.root, "master.key") }
    private val vault by lazy { MasterKeyVault(MasterKeyFileStore(keyFile), wrapper, random) }

    @Test
    fun freshInstallCreatesAKeyThatLoadsAgain() {
        val created = vault.loadOrCreate(hasEncryptedData = false) as VaultLoadResult.Loaded
        val loaded = vault.loadOrCreate(hasEncryptedData = true) as VaultLoadResult.Loaded

        assertTrue(created.created)
        assertFalse(loaded.created)
        assertTrue(sameKey(created.masterKey, loaded.masterKey))
    }

    @Test
    fun missingKeyFileWithExistingDataIsReportedAndNoNewKeyIsCreated() {
        val result = vault.loadOrCreate(hasEncryptedData = true)

        assertEquals(VaultLoadResult.Failed(VaultFailure.KeyFileMissing), result)
        assertFalse(keyFile.exists())
    }

    @Test
    fun damagedKeyFileIsReportedAndKeptOnDisk() {
        keyFile.writeBytes(byteArrayOf(1, 2, 3))

        val result = vault.loadOrCreate(hasEncryptedData = true)

        assertEquals(VaultLoadResult.Failed(VaultFailure.KeyFileCorrupt), result)
        assertTrue(keyFile.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun lostKeystoreKeyCanBeRecoveredWithTheRecoveryCode() {
        val original = (vault.loadOrCreate(hasEncryptedData = false) as VaultLoadResult.Loaded).masterKey
        val code = RecoveryCode.generate(random)
        vault.setRecoveryCode(original, code)
        wrapper.loseKey()

        val lost = vault.loadOrCreate(hasEncryptedData = true)
        val recovered = vault.recover(RecoveryCode.parse(code.toDisplayString())!!) as RecoveryResult.Recovered
        val reloaded = vault.loadOrCreate(hasEncryptedData = true) as VaultLoadResult.Loaded

        assertEquals(VaultLoadResult.Failed(VaultFailure.KeystoreKeyLost), lost)
        assertTrue(sameKey(original, recovered.masterKey))
        assertTrue(sameKey(original, reloaded.masterKey))
        assertTrue(vault.hasRecoveryCode())
    }

    @Test
    fun wrongOrMissingRecoveryCodeIsRejected() {
        val original = (vault.loadOrCreate(hasEncryptedData = false) as VaultLoadResult.Loaded).masterKey

        assertEquals(RecoveryResult.NotConfigured, vault.recover(RecoveryCode.generate(random)))

        vault.setRecoveryCode(original, RecoveryCode.generate(random))

        assertEquals(RecoveryResult.WrongCode, vault.recover(RecoveryCode.generate(random)))
    }

    @Test
    fun removingTheRecoveryCodeKeepsTheKeystoreSlot() {
        val original = (vault.loadOrCreate(hasEncryptedData = false) as VaultLoadResult.Loaded).masterKey
        vault.setRecoveryCode(original, RecoveryCode.generate(random))

        vault.removeRecoveryCode()
        val loaded = vault.loadOrCreate(hasEncryptedData = true) as VaultLoadResult.Loaded

        assertFalse(vault.hasRecoveryCode())
        assertTrue(sameKey(original, loaded.masterKey))
    }

    @Test
    fun startingOverKeepsTheSetAsideKeyFileOpenable() {
        val original = (vault.loadOrCreate(hasEncryptedData = false) as VaultLoadResult.Loaded).masterKey

        vault.moveKeyFileAside(".aside")
        val fresh = (vault.loadOrCreate(hasEncryptedData = false) as VaultLoadResult.Loaded).masterKey
        val asideVault = MasterKeyVault(MasterKeyFileStore(File(folder.root, "master.key.aside")), wrapper, random)
        val reopened = asideVault.loadOrCreate(hasEncryptedData = true) as VaultLoadResult.Loaded

        assertFalse(sameKey(original, fresh))
        assertTrue(sameKey(original, reopened.masterKey))
    }

    @Test
    fun slotsWithoutKeyIdFromEarlierBuildsStillOpen() {
        val raw = ByteArray(MasterKey.SIZE_BYTES).also(random::nextBytes)
        val sealed = wrapper.seal("", raw, MasterKeyFileCodec.slotAad(KeySlotType.Keystore))
        MasterKeyFileStore(keyFile).write(MasterKeyFile(listOf(KeySlot(KeySlotType.Keystore, ByteArray(0), sealed))))

        val loaded = vault.loadOrCreate(hasEncryptedData = true) as VaultLoadResult.Loaded

        assertTrue(sameKey(MasterKey.adopt(raw.copyOf()), loaded.masterKey))
    }

    @Test
    fun purposesGetIndependentKeys() {
        val key = (vault.loadOrCreate(hasEncryptedData = false) as VaultLoadResult.Loaded).masterKey

        val derived = KeyPurpose.entries.map { key.derive(it).toList() }

        assertEquals(derived.size, derived.toSet().size)
        assertEquals("MasterKey([REDACTED])", key.toString())
    }

    private fun sameKey(a: MasterKey, b: MasterKey): Boolean =
        a.derive(KeyPurpose.PrimaryDatabase).contentEquals(b.derive(KeyPurpose.PrimaryDatabase))

    /** Seals with in-memory AES keys per key ID; [loseKey] simulates wiped Keystore keys. */
    private class FakeKeyWrapper(private val random: SecureRandom) : KeyWrapper {
        private val keys = mutableMapOf<String, ByteArray>()

        override fun seal(keyId: String, plaintext: ByteArray, aad: ByteArray): ByteArray {
            val current = keys.getOrPut(keyId) { ByteArray(AesGcm.KEY_SIZE_BYTES).also(random::nextBytes) }
            return AesGcm.seal(current, plaintext, aad, random)
        }

        override fun open(keyId: String, box: ByteArray, aad: ByteArray): ByteArray {
            val current = keys[keyId] ?: throw KeyWrapperException(KeyWrapperFailure.KeyLost)
            return try {
                AesGcm.open(current, box, aad)
            } catch (e: GeneralSecurityException) {
                throw KeyWrapperException(KeyWrapperFailure.KeyLost, e)
            }
        }

        fun loseKey() {
            keys.clear()
        }
    }
}
