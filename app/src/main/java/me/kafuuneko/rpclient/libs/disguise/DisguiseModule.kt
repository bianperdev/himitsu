// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Koin module of the `feat/disguise` track, appended to `himitsuModules`. */
internal val disguiseModule = module {
    // Created with Koin at process start, before any screen, so an interrupted switch is
    // repaired whichever launcher entry the user taps next. It needs no database or key.
    single(createdAtStart = true) { AppearanceSwitcher(androidContext()).apply { reconcile() } }
}
