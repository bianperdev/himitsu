// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise

import me.kafuuneko.rpclient.feature.disguise.DisguiseUnlockEntry
import me.kafuuneko.rpclient.feature.lock.LockActivityEntry
import me.kafuuneko.rpclient.libs.disguise.compass.CompassSensor
import me.kafuuneko.rpclient.libs.disguise.secret.DisguiseSecretStore
import me.kafuuneko.rpclient.libs.security.UnlockEntry
import me.kafuuneko.rpclient.libs.security.lock.LockPolicy
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Koin module of the `feat/disguise` track, appended to `himitsuModules`. */
internal val disguiseModule = module {
    // Created with Koin at process start, before any screen, so an interrupted switch is
    // repaired whichever launcher entry the user taps next. It needs no database or key.
    // A failure is retried at the next start instead of crashing every launch.
    single(createdAtStart = true) {
        AppearanceSwitcher(androidContext()).apply {
            runCatching { reconcile() }
            // Turning disguise off deletes the shortcut after the icon switch; finish it if the
            // process died in between
            if (!current.isDecoy) get<DisguiseSecretStore>().let { if (it.hasData()) it.clear() }
        }
    }
    single { DisguiseSecretStore(androidContext()) }
    single { CompassSensor(androidContext()) }
    // Replaces the securityModule binding (Koin keeps the last definition), so a page hidden by
    // the lock shows the decoy while disguise is on. It delegates to the gate screen otherwise.
    single<UnlockEntry> { DisguiseUnlockEntry(get(), LockActivityEntry) }
    // Also replaces a securityModule binding: while disguise is on, leaving the app locks it at
    // once, so the decoy's icon or recents card never reopens a page within the lock timeout
    single { LockPolicy { isDisguiseActive() } }
}
