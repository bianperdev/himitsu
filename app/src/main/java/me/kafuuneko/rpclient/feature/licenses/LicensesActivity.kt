// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.licenses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.licenses.ui.LicenseDocument
import me.kafuuneko.rpclient.feature.licenses.ui.LicensesLayout
import me.kafuuneko.rpclient.libs.core.CoreActivity

/**
 * Open source licenses: NOTICE, the GPL and every third-party license text, read from the assets
 * that the `bundleLicenses` Gradle task copies from the repository, so both always match.
 */
class LicensesActivity : CoreActivity() {
    @Composable
    override fun ViewContent() {
        var documents by remember { mutableStateOf<List<LicenseDocument>>(emptyList()) }
        var opened by remember { mutableStateOf<LicenseDocument?>(null) }
        // Reading the asset list touches the APK file, so it runs off the main thread
        LaunchedEffect(Unit) { documents = withContext(Dispatchers.IO) { readDocuments() } }
        LicensesLayout(
            documents = documents,
            opened = opened,
            onOpen = { opened = it },
            onBack = { if (opened != null) opened = null else finish() }
        )
    }

    /** NOTICE first, then the project license, then the third-party texts by name. */
    private fun readDocuments(): List<LicenseDocument> {
        val names = assets.list(DIRECTORY).orEmpty().sortedWith(compareBy({ ORDER.indexOf(it).let { i -> if (i < 0) ORDER.size else i } }, { it }))
        return names.map { name ->
            LicenseDocument(name = name, text = assets.open("$DIRECTORY/$name").bufferedReader().use { it.readText() })
        }
    }

    private companion object {
        const val DIRECTORY = "licenses"
        val ORDER = listOf("NOTICE", "LICENSE")
    }
}
