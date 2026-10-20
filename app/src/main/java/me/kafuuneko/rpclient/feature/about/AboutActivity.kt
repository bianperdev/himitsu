package me.kafuuneko.rpclient.feature.about

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.content.pm.PackageInfoCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import me.kafuuneko.rpclient.feature.about.presentation.AboutUiState
import me.kafuuneko.rpclient.feature.about.presentation.AboutAppInfoState
import me.kafuuneko.rpclient.feature.about.ui.AboutLayout
import me.kafuuneko.rpclient.feature.licenses.LicensesActivity
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.CoreActivity

/** 关于页面宿主，负责提供版本、社区反馈和项目联系信息。 */
class AboutActivity : CoreActivity() {
    /** 提供安装包元数据，并将页面操作交给宿主执行。 */
    @Composable
    override fun ViewContent() {
        // - 读取应用包信息与静态链接，构造页面展示状态
        val uiState = remember {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            AboutUiState(
                appInfo = AboutAppInfoState(
                    versionName = packageInfo.versionName ?: getString(R.string.unknown_version),
                    buildNumber = PackageInfoCompat.getLongVersionCode(packageInfo).toString(),
                    isDevelopmentBuild = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                ),
                githubRepoUrl = AppModel.GITHUB_REPO,
                githubRepoName = AppModel.GITHUB_REPO.removePrefix("https://github.com/"),
                githubIssuesUrl = "${AppModel.GITHUB_REPO}/issues",
                linksRepository = FlavorAboutChannel.linksRepository,
                ratesInStore = FlavorAboutChannel.ratesInStore
            )
        }

        // - 渲染关于界面并绑定外部跳转交互
        Surface(modifier = Modifier.fillMaxSize()) {
            AboutLayout(
                uiState = uiState,
                onBack = { finish() },
                onOpenRepository = { safeOpenUrl(uiState.githubRepoUrl) },
                onRateApp = { FlavorAboutChannel.openStore(this) },
                onOpenFeedback = { safeOpenUrl(uiState.githubIssuesUrl) },
                onOpenLicenses = { startActivity(Intent(this, LicensesActivity::class.java)) },
                onOpenUpstream = { safeOpenUrl(RPCLIENT_REPO) }
            )
        }
    }

    /** 安全打开外部超链接，若无可用浏览器则回退至复制链接到剪贴板。 */
    private fun safeOpenUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText(getString(R.string.about), url)
            )
            Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        /** Himitsu is a fork of RPClient; credited in About (MIT notice in LICENSES/). */
        const val RPCLIENT_REPO = "https://github.com/KafuuNeko/RPClient"
    }
}
