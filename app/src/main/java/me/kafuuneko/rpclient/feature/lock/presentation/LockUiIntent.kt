// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock.presentation

import android.net.Uri
import me.kafuuneko.rpclient.libs.security.lock.AuthOutcome

/** User actions on the gate screen. */
sealed class LockUiIntent {
    data object Init : LockUiIntent()
    data object Resume : LockUiIntent()
    data object Unlock : LockUiIntent()
    data class AuthenticationFinished(val outcome: AuthOutcome) : LockUiIntent()
    data object ContinueWithoutDeviceLock : LockUiIntent()
    data object Retry : LockUiIntent()
    data object ShowRecoveryCodeDialog : LockUiIntent()

    /** Not a data class: its `toString` must never print the code. */
    class SubmitRecoveryCode(val code: String) : LockUiIntent() {
        override fun toString(): String = "SubmitRecoveryCode([REDACTED])"
    }

    data object ShowStartOverDialog : LockUiIntent()
    data object ConfirmStartOver : LockUiIntent()
    data object ShowSetAsideDialog : LockUiIntent()
    data object ConfirmSetAside : LockUiIntent()
    data object ShowExportDialog : LockUiIntent()
    data object ConfirmExport : LockUiIntent()

    /** The destination chosen in the system file picker, or null when the user canceled it. */
    data class ExportTo(val destination: Uri?) : LockUiIntent()
    data object DismissDialog : LockUiIntent()
}
