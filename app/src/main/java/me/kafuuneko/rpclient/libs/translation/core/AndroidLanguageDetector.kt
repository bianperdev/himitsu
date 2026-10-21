// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.core

import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.translation.LanguageDetector

/**
 * On-device language detection with Android's [TextClassificationManager] (API 29+).
 *
 * - The text never leaves the device: the system text classifier runs locally.
 * - The classifier may block, so it is called on [Dispatchers.Default].
 * - Returns the most likely language only when its confidence reaches [MIN_CONFIDENCE]; null when
 *   unsure, on API 26-28, or when the device has no working classifier.
 */
class AndroidLanguageDetector(private val context: Context) : LanguageDetector {

    override suspend fun detect(text: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || text.isBlank()) return null
        return withContext(Dispatchers.Default) {
            runCatching { detectOnClassifier(text.take(MAX_CHARS)) }.getOrNull()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun detectOnClassifier(text: String): String? {
        val manager = context.getSystemService(TextClassificationManager::class.java) ?: return null
        val result = manager.textClassifier.detectLanguage(TextLanguage.Request.Builder(text).build())
        // Pick the best hypothesis explicitly instead of relying on their order
        var bestTag: String? = null
        var bestScore = 0f
        for (index in 0 until result.localeHypothesisCount) {
            val locale = result.getLocale(index)
            val score = result.getConfidenceScore(locale)
            if (score > bestScore) {
                bestScore = score
                bestTag = locale.language
            }
        }
        return if (bestScore >= MIN_CONFIDENCE) normalizeLanguage(bestTag) else null
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.6f

        /** Enough text for a reliable guess, bounded to keep the classifier fast. */
        const val MAX_CHARS = 2000
    }
}
