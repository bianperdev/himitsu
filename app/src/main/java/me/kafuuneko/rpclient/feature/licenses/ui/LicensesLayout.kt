// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.licenses.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsTile

/** One bundled license file: [name] is its file name in the repository, [text] its content. */
data class LicenseDocument(val name: String, val text: String)

/** The list of license files, or the text of [opened]. */
@Composable
fun LicensesLayout(
    documents: List<LicenseDocument>,
    opened: LicenseDocument?,
    onOpen: (LicenseDocument) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { AppTopBar(title = opened?.let { titleOf(it.name) } ?: stringResource(R.string.licenses_title), onBack = onBack) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (opened != null) {
                // License texts are plain text with fixed line breaks
                SelectionContainer {
                    Text(opened.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            } else {
                RpInfoCard(
                    icon = Icons.Rounded.Gavel,
                    title = stringResource(R.string.licenses_title),
                    subtitle = stringResource(R.string.licenses_desc)
                )
                RpSettingsGroup {
                    documents.forEachIndexed { index, document ->
                        if (index > 0) RpSettingsDivider()
                        RpSettingsTile(
                            title = titleOf(document.name),
                            subtitle = document.name,
                            icon = Icons.AutoMirrored.Rounded.Article,
                            onClick = { onOpen(document) }
                        )
                    }
                }
            }
        }
    }
}

/** Readable titles for the two project files; third-party files are named after their license. */
@Composable
private fun titleOf(name: String): String = when (name) {
    "NOTICE" -> stringResource(R.string.licenses_notice)
    "LICENSE" -> stringResource(R.string.licenses_gpl)
    else -> name.removeSuffix(".txt")
}
