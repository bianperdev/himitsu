// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.feature.lock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import me.kafuuneko.rpclient.feature.lock.presentation.LockUiIntent
import me.kafuuneko.rpclient.feature.lock.presentation.LockUiState
import me.kafuuneko.rpclient.feature.lock.ui.LockLayout
import me.kafuuneko.rpclient.feature.main.MainActivity
import me.kafuuneko.rpclient.libs.security.UnlockEntry
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Back must never reveal the page underneath; it leaves the app instead
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
        setContent {
            val themeMode by mAppThemeManager.themeModeFlow.collectAsState()
            val uiState by mViewModel.uiStateFlow.collectAsState()
            LaunchedEffect(uiState) {
                if (uiState is LockUiState.Finished) leave()
            }
            AppTheme(darkTheme = themeMode.resolveDarkTheme(isSystemInDarkTheme())) {
                LockLayout(uiState = uiState, emit = { mViewModel.emit(this) })
            }
        }
        mViewModel.emit(LockUiIntent.Init)
    }

    /** Returns to the page underneath, or to the main page when the gate was opened directly. */
    private fun leave() {
        if (isTaskRoot) startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

/** [UnlockEntry] that opens [LockActivity]. */
object LockActivityEntry : UnlockEntry {
    override fun open(context: Context) {
        val intent = Intent(context, LockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
