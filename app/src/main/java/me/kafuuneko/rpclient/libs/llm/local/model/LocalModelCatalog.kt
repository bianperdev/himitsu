// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local.model

import androidx.annotation.StringRes
import me.kafuuneko.rpclient.R

/**
 * A model the user can download from Hugging Face with one tap.
 *
 * Every field is fixed in the app: browsing the list makes no network call, the download URL
 * points to a pinned commit, and the file is accepted only if its size and SHA-256 match.
 */
data class LocalModelCatalogEntry(
    /** Stable key, used for the partial download file name. */
    val key: String,
    val displayName: String,
    @param:StringRes val descriptionRes: Int,
    val repository: String,
    /** Commit of [repository] the file is downloaded from. */
    val revision: String,
    val fileName: String,
    val sizeBytes: Long,
    /** Lowercase hex SHA-256, as published by Hugging Face for the LFS file. */
    val sha256: String,
    /** SPDX identifier of the model weights' license. */
    val license: String,
    /** Approximate memory at a 4096-token context, from the design document. */
    val approximateRamBytes: Long
) {
    /** Direct link to the pinned file. */
    val downloadUrl: String
        get() = "https://huggingface.co/$repository/resolve/$revision/$fileName"
}

/**
 * Curated download list. Only Apache-2.0 models without access restrictions are listed; models
 * with other licenses can still be imported by hand.
 *
 * Entries verified against the Hugging Face API on 2026-10-20.
 */
object LocalModelCatalog {
    private const val GB = 1_000_000_000L

    val entries: List<LocalModelCatalogEntry> = listOf(
        LocalModelCatalogEntry(
            key = "qwen3-4b-instruct-2507-q4km",
            displayName = "Qwen3 4B Instruct 2507",
            descriptionRes = R.string.local_models_catalog_qwen3_4b,
            repository = "unsloth/Qwen3-4B-Instruct-2507-GGUF",
            revision = "a06e946bb6b655725eafa393f4a9745d460374c9",
            fileName = "Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
            sizeBytes = 2_497_281_120L,
            sha256 = "3605803b982cb64aead44f6c1b2ae36e3acdb41d8e46c8a94c6533bc4c67e597",
            license = "Apache-2.0",
            approximateRamBytes = 32 * GB / 10
        ),
        LocalModelCatalogEntry(
            key = "gemma-4-e4b-it-q4km",
            displayName = "Gemma 4 E4B it",
            descriptionRes = R.string.local_models_catalog_gemma4_e4b,
            repository = "unsloth/gemma-4-E4B-it-GGUF",
            revision = "bfc15c382204943c3a8fff0c750b94ae2364d7a3",
            fileName = "gemma-4-E4B-it-Q4_K_M.gguf",
            sizeBytes = 4_977_171_584L,
            sha256 = "85a896a047553e842f25297ee5b031d64ff30147d9c4af17b1e4b394cd1fab87",
            license = "Apache-2.0",
            approximateRamBytes = 55 * GB / 10
        ),
        LocalModelCatalogEntry(
            key = "gemma-4-e2b-it-q4km",
            displayName = "Gemma 4 E2B it",
            descriptionRes = R.string.local_models_catalog_gemma4_e2b,
            repository = "unsloth/gemma-4-E2B-it-GGUF",
            revision = "0314792d7f1f7e229411f620751375812bb9faf2",
            fileName = "gemma-4-E2B-it-Q4_K_M.gguf",
            sizeBytes = 3_106_738_272L,
            sha256 = "740185b21d22ceb83a11c3aa62ad5842ef32c70f6096d756bbee85a1e4ec34b8",
            license = "Apache-2.0",
            approximateRamBytes = 35 * GB / 10
        ),
        LocalModelCatalogEntry(
            key = "ministral-3-3b-instruct-2512-q4km",
            displayName = "Ministral 3 3B Instruct 2512",
            descriptionRes = R.string.local_models_catalog_ministral3_3b,
            repository = "unsloth/Ministral-3-3B-Instruct-2512-GGUF",
            revision = "7564922f37fa5bbb62b87f09a55c12f1f91d7a6a",
            fileName = "Ministral-3-3B-Instruct-2512-Q4_K_M.gguf",
            sizeBytes = 2_146_497_824L,
            sha256 = "fd46fc371ff0509bfa8657ac956b7de8534d7d9baaa4947975c0648c3aa397f4",
            license = "Apache-2.0",
            approximateRamBytes = 27 * GB / 10
        ),
        LocalModelCatalogEntry(
            key = "qwen3.5-2b-q4km",
            displayName = "Qwen3.5 2B",
            descriptionRes = R.string.local_models_catalog_qwen35_2b,
            repository = "unsloth/Qwen3.5-2B-GGUF",
            revision = "f6d5376be1edb4d416d56da11e5397a961aca8ae",
            fileName = "Qwen3.5-2B-Q4_K_M.gguf",
            sizeBytes = 1_280_835_840L,
            sha256 = "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223",
            license = "Apache-2.0",
            approximateRamBytes = 17 * GB / 10
        )
    )

    fun find(key: String): LocalModelCatalogEntry? = entries.firstOrNull { it.key == key }
}
