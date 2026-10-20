// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock.presentation

/** User actions on the gate screen. */
sealed class LockUiIntent {
    data object Init : LockUiIntent()
    data object Retry : LockUiIntent()
    data object ShowRecoveryCodeDialog : LockUiIntent()

    /** Not a data class: its `toString` must never print the code. */
    class SubmitRecoveryCode(val code: String) : LockUiIntent() {
        override fun toString(): String = "SubmitRecoveryCode([REDACTED])"
    }

    data object ShowStartOverDialog : LockUiIntent()
    data object ConfirmStartOver : LockUiIntent()
    data object DismissDialog : LockUiIntent()
}
