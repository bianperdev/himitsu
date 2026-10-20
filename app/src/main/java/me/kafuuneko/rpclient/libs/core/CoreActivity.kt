package me.kafuuneko.rpclient.libs.core

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.libs.disguise.DisguiseTaskDescription
import me.kafuuneko.rpclient.libs.disguise.QuickExit
import me.kafuuneko.rpclient.libs.security.AppGate
import me.kafuuneko.rpclient.libs.security.GateState
import me.kafuuneko.rpclient.libs.security.LocalAppGateOpen
import me.kafuuneko.rpclient.libs.security.PrivateTextInput
import me.kafuuneko.rpclient.libs.security.UnlockEntry
import me.kafuuneko.rpclient.libs.security.lock.SecurityPrefs
import me.kafuuneko.rpclient.libs.security.lock.WindowProtection
import me.kafuuneko.rpclient.libs.theme.AppThemeManager
import me.kafuuneko.rpclient.ui.theme.AppTheme
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Compose Activity 基类。
 *
 * 统一应用主题和 Edge-to-Edge 配置，具体页面只实现 [ViewContent]。
 */
abstract class CoreActivity : ComponentActivity(), KoinComponent {
    private val mAppThemeManager by inject<AppThemeManager>()
    private val mAppGate by inject<AppGate>()
    private val mUnlockEntry by inject<UnlockEntry>()

    /**
     * Curtain raised when the page leaves the screen with the app lock on, and lowered in
     * [onResume] only after a synchronous gate check (Himitsu). The gate flow is asynchronous,
     * so without it a re-locked page could draw a frame before the curtain.
     */
    private val mForcedCurtain = mutableStateOf(false)

    /** 子类可关闭默认的 Edge-to-Edge。 */
    protected open fun isEnableEdgeToEdge(): Boolean = true

    /** 页面 Compose 内容入口。 */
    @Composable
    protected abstract fun ViewContent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initView()
        observeAppGate()
    }

    /** Re-applies the screen privacy settings, which may have changed on another page (Himitsu, P-12). */
    override fun onResume() {
        super.onResume()
        WindowProtection.apply(this)
        // feat/disguise: recents show the decoy's label and icon while disguise is on
        DisguiseTaskDescription.apply(this)
        // Only a blocked gate keeps the curtain; Starting is covered by the gate state itself
        mForcedCurtain.value = mAppGate.evaluateNow() == GateState.Blocked
    }

    /** feat/disguise: a double press of volume down shows the decoy, when the user turned it on. */
    // androidx.core marks its ComponentActivity.dispatchKeyEvent override as restricted, but
    // overriding the public Activity method and calling super is the documented use
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        QuickExit.onKeyEvent(this, event) || super.dispatchKeyEvent(event)

    /** Covers the page before it can be seen again if the app may lock meanwhile (Himitsu). */
    override fun onStop() {
        super.onStop()
        if (SecurityPrefs.appLockEnabled) mForcedCurtain.value = true
    }

    /** Opens the gate screen whenever this page is visible and the gate is blocked (Himitsu). */
    private fun observeAppGate() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mAppGate.state.collect { state ->
                    if (state == GateState.Open && mAppGate.evaluateNow() == GateState.Open) mForcedCurtain.value = false
                    if (state == GateState.Blocked) mUnlockEntry.open(this@CoreActivity)
                }
            }
        }
    }

    private fun initView() {
        if (isEnableEdgeToEdge()) {
            enableEdgeToEdge()
        }
        setContent {
            val themeMode by mAppThemeManager.themeModeFlow.collectAsState()
            val dynamicColor by mAppThemeManager.dynamicColorFlow.collectAsState()
            val darkTheme = themeMode.resolveDarkTheme(isSystemInDarkTheme())
            ApplySystemBarTheme(darkTheme)
            AppTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = getContent())
        }
    }

    private fun getContent(): @Composable () -> Unit = { GatedContent() }

    /**
     * Page content behind the app gate (Himitsu).
     *
     * While the gate is not open, an opaque curtain covers the page, takes every touch and
     * hides the page from accessibility services. The page stays composed so its state
     * survives a lock. Copying and typing on every page are kept private ([PrivateTextInput]).
     */
    @Composable
    private fun GatedContent() {
        val gateState by mAppGate.state.collectAsState()
        val open = gateState == GateState.Open && !mForcedCurtain.value
        Box(Modifier.fillMaxSize()) {
            Box(if (open) Modifier.fillMaxSize() else Modifier.fillMaxSize().clearAndSetSemantics { }) {
                CompositionLocalProvider(LocalAppGateOpen provides open) {
                    PrivateTextInput { ViewContent() }
                }
            }
            if (!open) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                )
            }
        }
    }

    /** 让 Edge-to-Edge 系统栏图标与强制亮色或暗色主题保持可读对比度。 */
    @Composable
    private fun ApplySystemBarTheme(darkTheme: Boolean) {
        if (!isEnableEdgeToEdge()) return
        SideEffect {
            val style = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
                detectDarkMode = { darkTheme }
            )
            enableEdgeToEdge(
                statusBarStyle = style,
                navigationBarStyle = style
            )
        }
    }
}

/** 为项目 Compose 组件提供与真实 Activity 一致的主题预览环境。 */
@Composable
fun ActivityPreview(darkTheme: Boolean, content: @Composable () -> Unit) {
    AppTheme(darkTheme = darkTheme, content = content)
}
