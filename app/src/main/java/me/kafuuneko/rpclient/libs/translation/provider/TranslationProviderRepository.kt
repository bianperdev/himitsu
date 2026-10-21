// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.provider

import com.chibatching.kotpref.KotprefModel
import me.kafuuneko.rpclient.libs.translation.TranslationClientFactory
import me.kafuuneko.rpclient.libs.translation.TranslationPrefs
import me.kafuuneko.rpclient.libs.translation.TranslationProviderConfig
import me.kafuuneko.rpclient.libs.translation.model.TranslationProvider
import me.kafuuneko.rpclient.libs.translation.model.TranslationProviderDao

/**
 * Stores the translation services the user configures and keeps the default provider valid.
 *
 * - Edits use `update`, never `insertOrReplace`: SQLite's REPLACE deletes the row first, and the
 *   foreign keys of stored translations and chat settings would be set to null.
 * - Deleting the default provider moves the default to the next one, or to none.
 */
class TranslationProviderRepository(
    private val mDao: TranslationProviderDao,
    private val mClientFactory: TranslationClientFactory
) {
    suspend fun getAll(): List<TranslationProvider> = mDao.getAll()

    suspend fun getById(id: Long): TranslationProvider? = mDao.getById(id)

    /**
     * Inserts a new provider (id 0) or updates an existing one, and returns its id. The first
     * provider becomes the default when none is chosen.
     */
    suspend fun save(provider: TranslationProvider): Long {
        val id = if (provider.id == 0L) {
            mDao.insertOrIgnore(provider.copy(createTime = System.currentTimeMillis()))
        } else {
            mDao.update(provider)
            provider.id
        }
        if (TranslationPrefs.defaultProviderId == TranslationPrefs.NO_PROVIDER || mDao.getById(TranslationPrefs.defaultProviderId) == null) {
            TranslationPrefs.defaultProviderId = id
        }
        return id
    }

    /** Deletes a provider; translations and chat settings that used it keep their rows with no provider. */
    suspend fun delete(id: Long) {
        mDao.deleteById(id)
        if (TranslationPrefs.defaultProviderId == id) {
            TranslationPrefs.defaultProviderId = mDao.getAll().firstOrNull()?.id ?: TranslationPrefs.NO_PROVIDER
        }
    }

    /**
     * Translates [TEST_SAMPLE], a fixed sentence and never chat text, with an unsaved
     * configuration, so the editor can check the endpoint and key before saving.
     */
    suspend fun test(config: TranslationProviderConfig, target: String): String =
        mClientFactory.create(config).translate(listOf(TEST_SAMPLE), source = TEST_SAMPLE_LANGUAGE, target = target).first()

    companion object {
        const val TEST_SAMPLE = "Hello! The translation service works."
        const val TEST_SAMPLE_LANGUAGE = "en"
    }
}

/** Device-level flags of the translation settings screens; nothing sensitive. */
object TranslationProviderPrefs : KotprefModel() {
    override val kotprefName: String = "translation_providers"

    /** The user read what translation sends and to whom; shown again until they accept it once. */
    var dataNoticeAccepted by booleanPref(default = false)
}
