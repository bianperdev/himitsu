package me.kafuuneko.rpclient.feature.about.presentation

/** 关于页展示的外部联系与项目信息。 */
data class AboutUiState(
    /** 关于页面展示的应用版本名称。 */
    val appInfo: AboutAppInfoState,
    /** 关于页面打开项目主页所使用的仓库地址。 */
    val githubRepoUrl: String,
    /** 关于页面展示的代码仓库名称。 */
    val githubRepoName: String,
    /** 关于页面打开问题反馈所使用的仓库地址。 */
    val githubIssuesUrl: String = "$githubRepoUrl/issues",
    /** Himitsu: whether this channel links the repository and the issue tracker (not `play`). */
    val linksRepository: Boolean = true,
    /** Himitsu: whether this channel shows the "rate the app" card (only `play`). */
    val ratesInStore: Boolean = false
)

/** 品牌标识与应用信息分组共用的安装包元数据。 */
data class AboutAppInfoState(
    val versionName: String,
    val buildNumber: String,
    val isDevelopmentBuild: Boolean
)
