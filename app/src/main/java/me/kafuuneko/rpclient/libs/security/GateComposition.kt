// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether app content may be drawn, provided by `CoreActivity` for its page.
 *
 * The page curtain only covers the Activity window; dialogs open their own windows above it.
 * Dialog hosts that can show private content read this and skip their window while it is
 * false; their state lives in the ViewModel, so they come back unchanged after unlocking.
 * Defaults to true for screens outside the gate, such as the gate screen itself and previews.
 */
val LocalAppGateOpen = staticCompositionLocalOf { true }
