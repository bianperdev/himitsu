// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.lock

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Outcome of an unlock attempt, reported to the gate screen. */
sealed class AuthOutcome {
    data object Succeeded : AuthOutcome()

    /** The user dismissed the prompt. */
    data object Canceled : AuthOutcome()

    /** The system refused, for example after too many attempts. [message] comes localized from the system. */
    data class Error(val message: String) : AuthOutcome()
}

/**
 * The app lock: decides when the app is locked and unlocks it only after authentication.
 *
 * - Locked on every cold start when enabled, and again after the app spent [LockTimeout] in the
 *   background, measured with `elapsedRealtime` so changing the clock does not help.
 * - Unlocking happens only inside the [BiometricPrompt] success callback created here; there is
 *   no public unlock method, so no other entry point (such as a disguise decoy) can skip it.
 * - The lock is a gate in front of the UI. The data is encrypted independently with a key that
 *   is not bound to authentication, so a changed fingerprint or a removed screen lock can never
 *   make data unreadable.
 * - When the device has no secure screen lock, authentication is impossible; the user may then
 *   continue after an explicit warning instead of being locked out.
 */
class AppLockManager(
    private val mContext: Context,
    private val mPolicy: LockPolicy = LockPolicy.Default
) : DefaultLifecycleObserver {
    private val mLocked = MutableStateFlow(SecurityPrefs.appLockEnabled)
    private var mBackgroundSince: Long? = null
    private var mAuthenticating = false

    /** Whether the app is locked right now. */
    val isLocked: StateFlow<Boolean> = mLocked.asStateFlow()

    /** Whether a prompt is on screen; a second one must not be stacked on top. */
    val isAuthenticating: Boolean
        get() = mAuthenticating

    /**
     * How many times the app really went to the background. The prompt's own credential screen
     * does not count, so the gate screen can tell a return to the app from a dismissed prompt.
     */
    var backgroundCount: Int = 0
        private set

    init {
        // The process lifecycle must be observed from the main thread
        ContextCompat.getMainExecutor(mContext).execute {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }
    }

    /** Whether the device has a PIN, pattern, password or biometrics behind a credential. */
    fun isDeviceSecure(): Boolean =
        mContext.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /**
     * Forgets a prompt that is no longer on screen. The platform can drop the prompt's callback
     * (API 26-29); without this, "Unlock" would do nothing until the process ends.
     */
    fun clearStaleAuthentication() {
        mAuthenticating = false
    }

    /** Turns the lock on or off. Turning it on does not lock the current session. */
    fun setEnabled(enabled: Boolean) {
        SecurityPrefs.appLockEnabled = enabled
        if (!enabled) mLocked.value = false
    }

    /** Current timeout. */
    var timeout: LockTimeout
        get() = LockTimeout.fromName(SecurityPrefs.lockTimeout)
        set(value) {
            SecurityPrefs.lockTimeout = value.name
        }

    /**
     * Creates the prompt for [activity]. Must be called from `onCreate`, as `BiometricPrompt`
     * requires, and [onOutcome] runs on the main thread.
     */
    fun createPrompt(activity: FragmentActivity, onOutcome: (AuthOutcome) -> Unit): BiometricPrompt =
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    mAuthenticating = false
                    mBackgroundSince = null
                    mLocked.value = false
                    onOutcome(AuthOutcome.Succeeded)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    mAuthenticating = false
                    val canceled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    onOutcome(if (canceled) AuthOutcome.Canceled else AuthOutcome.Error(errString.toString()))
                }
            }
        )

    /**
     * Creates a prompt that only confirms the user before a sensitive settings change. Unlike
     * [createPrompt] it never changes the lock state. Must be called from `onCreate`, and
     * [onOutcome] runs on the main thread.
     */
    fun createConfirmPrompt(activity: FragmentActivity, onOutcome: (AuthOutcome) -> Unit): BiometricPrompt =
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onOutcome(AuthOutcome.Succeeded)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onOutcome(AuthOutcome.Canceled)
                }
            }
        )

    /** Shows [prompt] with the strongest authenticators the platform allows together with the device credential. */
    fun authenticate(prompt: BiometricPrompt, title: String, subtitle: String) {
        mAuthenticating = true
        prompt.authenticate(promptInfo(title, subtitle))
    }

    /**
     * Shows a prompt made by [createConfirmPrompt]. Unlike [authenticate] it does not mark the
     * app as authenticating: the app is unlocked, so leaving it for the credential screen
     * counts as leaving, and the lock timeout still applies when the user comes back.
     */
    fun confirm(prompt: BiometricPrompt, title: String, subtitle: String) {
        prompt.authenticate(promptInfo(title, subtitle))
    }

    private fun promptInfo(title: String, subtitle: String): BiometricPrompt.PromptInfo =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators())
            .setConfirmationRequired(false)
            .build()

    /**
     * Unlocks without authentication, only when the device has no secure lock screen and so
     * nothing could be checked. Removing the system screen lock already required the credential.
     *
     * @return false when the device is secure, in which case nothing changes
     */
    fun continueWithoutDeviceLock(): Boolean {
        if (isDeviceSecure()) return false
        mBackgroundSince = null
        mLocked.value = false
        return true
    }

    override fun onStop(owner: LifecycleOwner) {
        // The credential screen of the prompt puts the app in the background; that is not leaving
        if (mAuthenticating) return
        backgroundCount++
        mBackgroundSince = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        val since = mBackgroundSince ?: return
        mBackgroundSince = null
        if (!SecurityPrefs.appLockEnabled) return
        // A stricter policy (disguise) locks at once; the timeout itself stays as the user set it
        val limit = if (mPolicy.lockOnLeave()) 0L else timeout.millis
        if (SystemClock.elapsedRealtime() - since >= limit) mLocked.value = true
    }

    /** Strong biometrics with the credential need API 30; earlier versions only allow weak ones. */
    private fun allowedAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
        } else {
            Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        }
}
