// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock.presentation

import androidx.annotation.StringRes
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** One-time actions the gate screen host must perform. */
sealed class LockViewEvent : IViewEvent {
    /** Show the system authentication prompt. */
    data object ShowAuthenticationPrompt : LockViewEvent()

    /** Let the user choose where to save an export, suggesting [fileName]. */
    data class PickExportDestination(val fileName: String) : LockViewEvent()

    /** Ask the user to confirm with biometrics or the device credential. */
    data object RequestConfirmation : LockViewEvent()

    /** Restart the app, so a recorded restore finishes before anything opens the files. */
    data object RestartApp : LockViewEvent()

    /** Show a short message. */
    data class ShowMessage(@StringRes val message: Int) : LockViewEvent()
}
