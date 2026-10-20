// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.FragmentActivity
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.security.lock.AppLockManager
import me.kafuuneko.rpclient.libs.security.lock.AuthOutcome
import me.kafuuneko.rpclient.libs.security.lock.WindowProtection
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Asks the user to confirm with biometrics or the device credential before a sensitive change,
 * such as turning the app lock off or changing the recovery code.
 *
 * A transparent, stateless screen: it shows only the system prompt and returns
 * [Activity.RESULT_OK] solely from the prompt's success callback. Every other way out (cancel,
 * back, an error, the app going to the background) returns [Activity.RESULT_CANCELED]. Use it
 * through [ConfirmCredentialContract]. It never changes the lock state.
 *
 * When the device has no screen lock there is nothing to check, so it confirms at once, as the
 * gate does: removing the system screen lock already required the credential.
 */
class ConfirmCredentialActivity : FragmentActivity(), KoinComponent {
    private val mLockManager by inject<AppLockManager>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The prompt is created in onCreate so it survives configuration changes
        val prompt = mLockManager.createConfirmPrompt(this) { outcome ->
            setResult(if (outcome == AuthOutcome.Succeeded) Activity.RESULT_OK else Activity.RESULT_CANCELED)
            finish()
        }
        if (savedInstanceState != null) return
        if (!mLockManager.isDeviceSecure()) {
            setResult(Activity.RESULT_OK)
            finish()
            return
        }
        mLockManager.confirm(
            prompt = prompt,
            title = getString(R.string.security_confirm_prompt_title),
            subtitle = getString(R.string.security_confirm_prompt_subtitle)
        )
    }

    override fun onResume() {
        super.onResume()
        WindowProtection.apply(this)
    }
}

/** Starts [ConfirmCredentialActivity]; the result is true only after a successful confirmation. */
class ConfirmCredentialContract : ActivityResultContract<Unit, Boolean>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(context, ConfirmCredentialActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == Activity.RESULT_OK
}
