package me.kafuuneko.rpclient.libs.room.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.adapter.ImageLogSanitizer
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import me.kafuuneko.rpclient.libs.room.entity.LLMRequestLog
import me.kafuuneko.rpclient.libs.room.model.LLMRequestLogOverview
import me.kafuuneko.rpclient.libs.security.RequestLogRedactor

/** 调试请求日志仓库；只有开启 [AppModel.debugModeEnabled] 时才写入原始内容。 */
class LLMRequestLogRepository(
    private val mRequestLogDatabase: RequestLogDatabase
) {
    private val mLLMRequestLogDao = mRequestLogDatabase.getLLMRequestLogDao()

    /** 按最新优先读取列表摘要，不把完整原始载荷带入页面状态。 */
    suspend fun getLogOverviews(
        previewLength: Int,
        limit: Int,
        offset: Int
    ): List<LLMRequestLogOverview> {
        return mLLMRequestLogDao.getLogOverviews(previewLength, limit, offset)
    }

    /** 按需读取单条日志的完整请求 JSON。 */
    suspend fun getRequestJson(id: Long): String? {
        return mLLMRequestLogDao.getRequestJson(id)
    }

    /** 按需读取单条日志的完整响应 JSON。 */
    suspend fun getResponseJson(id: Long): String? {
        return mLLMRequestLogDao.getResponseJson(id)
    }

    /**
     * 条件写入一次完整请求/响应；非调试模式直接返回 0。
     *
     * Himitsu (P-07): credentials are redacted before storage and old logs are pruned after
     * each write; the database itself is encrypted with SQLCipher.
     */
    suspend fun saveLog(
        provider: LLMProviderConfig,
        model: String,
        isStreaming: Boolean,
        requestJson: String,
        responseJson: String
    ): Long {
        if (!AppModel.debugModeEnabled) return 0L
        val secrets = RequestLogRedactor.providerSecrets(provider.apiKey, provider.customHeadersJson)
        val id = mLLMRequestLogDao.insertOrReplace(
            LLMRequestLog(
                providerName = provider.name,
                providerType = provider.providerType,
                protocol = provider.protocol,
                model = model,
                isStreaming = isStreaming,
                requestJson = RequestLogRedactor.redact(ImageLogSanitizer.sanitize(requestJson), secrets),
                responseJson = RequestLogRedactor.redact(ImageLogSanitizer.sanitize(responseJson), secrets)
            )
        )
        pruneExpired()
        return id
    }

    /** Himitsu (P-07): applies the retention limits (age and count). */
    suspend fun pruneExpired(now: Long = System.currentTimeMillis()) {
        mLLMRequestLogDao.deleteOlderThan(now - MAX_AGE_MILLIS)
        mLLMRequestLogDao.keepNewest(MAX_LOGS)
    }

    /**
     * 清空本地调试日志。
     *
     * Himitsu (P-07): VACUUM rewrites the file so the deleted pages are not left behind.
     */
    suspend fun deleteAll() {
        mLLMRequestLogDao.deleteAll()
        // Best effort: VACUUM needs free space, and a full disk must not break startup
        withContext(Dispatchers.IO) {
            runCatching { mRequestLogDatabase.openHelper.writableDatabase.execSQL("VACUUM") }
        }
    }

    private companion object {
        /** Debug logs older than seven days are deleted. */
        const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000

        /** At most this many debug logs are kept. */
        const val MAX_LOGS = 200
    }
}
