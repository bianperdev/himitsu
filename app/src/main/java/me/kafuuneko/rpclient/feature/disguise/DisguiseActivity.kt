// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.disguise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import me.kafuuneko.rpclient.feature.disguise.ui.DisguiseTheme

/**
 * Screen behind the decoy launcher aliases.
 *
 * It must work while the app is locked, so it does not extend `CoreActivity` and never shows
 * user data (security contract). The decoy screens are added by the next milestones of
 * `feat/disguise`; until then no alias that opens this screen can be enabled from the app.
 */
class DisguiseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DisguiseTheme {
                Surface(Modifier.fillMaxSize()) {}
            }
        }
    }
}
