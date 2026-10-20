// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.lock.presentation.LockUiIntent
import me.kafuuneko.rpclient.feature.lock.presentation.LockUiState
import me.kafuuneko.rpclient.feature.lock.presentation.LockViewEvent
import me.kafuuneko.rpclient.feature.lock.ui.LockLayout
import me.kafuuneko.rpclient.feature.main.MainActivity
import me.kafuuneko.rpclient.libs.security.UnlockEntry
import me.kafuuneko.rpclient.libs.security.lock.AppLockManager
import me.kafuuneko.rpclient.libs.security.lock.WindowProtection
import me.kafuuneko.rpclient.libs.theme.AppThemeManager
import me.kafuuneko.rpclient.ui.theme.AppTheme
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The gate screen: shown on top of any page while the app gate is not open.
 *
 * It is a [FragmentActivity] rather than a `CoreActivity` because `BiometricPrompt` needs one,
 * and because it must not be hidden behind the gate it implements.
 */
class LockActivity : FragmentActivity(), KoinComponent {
    private val mViewModel by viewModels<LockViewModel>()
    private val mAppThemeManager by inject<AppThemeManager>()
    private val mLockManager by inject<AppLockManager>()
    private lateinit var mPrompt: BiometricPrompt
    private val mExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        mViewModel.emit(LockUiIntent.ExportTo(uri))
    }
    private val mConfirmLauncher = registerForActivityResult(ConfirmCredentialContract()) { confirmed ->
        mViewModel.emit(LockUiIntent.ConfirmationFinished(confirmed))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Back must never reveal the page underneath; it leaves the app instead
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
        // BiometricPrompt must be created in onCreate to survive configuration changes
        mPrompt = mLockManager.createPrompt(this) { outcome ->
            mViewModel.emit(LockUiIntent.AuthenticationFinished(outcome))
        }
        setContent {
            val themeMode by mAppThemeManager.themeModeFlow.collectAsState()
            val uiState by mViewModel.uiStateFlow.collectAsState()
            LaunchedEffect(uiState) {
                (uiState as? LockUiState.Finished)?.let { leave(it.restartApp) }
            }
            AppTheme(darkTheme = themeMode.resolveDarkTheme(isSystemInDarkTheme())) {
                LockLayout(uiState = uiState, emit = { mViewModel.emit(this) })
            }
        }
        collectViewEvents()
        mViewModel.emit(LockUiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        WindowProtection.apply(this)
        // A prompt counted as showing must have its fragment; otherwise its callback was lost.
        // Checked one frame later, so a success callback that arrives after onResume comes first.
        window.decorView.post {
            if (isFinishing || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@post
            supportFragmentManager.executePendingTransactions()
            if (mLockManager.isAuthenticating && supportFragmentManager.findFragmentByTag(BIOMETRIC_FRAGMENT_TAG) == null) {
                mLockManager.clearStaleAuthentication()
            }
        }
        mViewModel.emit(LockUiIntent.Resume)
    }

    private fun collectViewEvents() {
        lifecycleScope.launch {
            mViewModel.viewEventFlow
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { wrapper ->
                    wrapper.consumeIfNotHandled { event ->
                        when (event) {
                            LockViewEvent.ShowAuthenticationPrompt -> if (!mLockManager.isAuthenticating) {
                                mLockManager.authenticate(
                                    prompt = mPrompt,
                                    title = getString(R.string.security_lock_prompt_title),
                                    subtitle = getString(R.string.security_lock_prompt_subtitle)
                                )
                            }
                            is LockViewEvent.PickExportDestination -> mExportLauncher.launch(event.fileName)
                            LockViewEvent.RequestConfirmation -> mConfirmLauncher.launch(Unit)
                            LockViewEvent.RestartApp -> {
                                startActivity(
                                    Intent(this@LockActivity, MainActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                )
                                finishAffinity()
                                Runtime.getRuntime().exit(0)
                            }
                            is LockViewEvent.ShowMessage ->
                                Toast.makeText(this@LockActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        }
    }

    /**
     * Returns to the page underneath, or to the main page when the gate was opened directly.
     *
     * @param restartApp clears the task first, so no screen keeps values read without the key
     */
    private fun leave(restartApp: Boolean) {
        when {
            restartApp -> startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            isTaskRoot -> startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}

/** Tag under which `androidx.biometric` 1.1.0 adds the fragment of a prompt on screen. */
private const val BIOMETRIC_FRAGMENT_TAG = "androidx.biometric.BiometricFragment"

/** [UnlockEntry] that opens [LockActivity]. */
object LockActivityEntry : UnlockEntry {
    override fun open(context: Context) {
        val intent = Intent(context, LockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
