// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local.model

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Foreground service that downloads one catalog model at a time.
 *
 * - Runs only after the user confirmed a download; it is never started on its own.
 * - The notification is deliberately neutral ("Downloading file"), with no model name, so it
 *   reveals nothing at a glance and stays compatible with disguise mode.
 * - Progress goes to [LocalModelDownloadTracker], which the models screen observes.
 */
class LocalModelDownloadService : Service(), KoinComponent {
    private val mDownloader by inject<LocalModelDownloader>()
    private val mTracker by inject<LocalModelDownloadTracker>()
    private val mScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mJob: Job? = null
    private var mCurrentKey: String? = null
    private var mShownPercent = -1

    /** Set when Android ends the foreground time, so the stop is reported instead of hidden. */
    @Volatile
    private var mTimedOut = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val entry = intent?.getStringExtra(EXTRA_KEY)?.let(LocalModelCatalog::find)
        when (intent?.action) {
            ACTION_CANCEL -> cancel(entry)
            ACTION_START -> if (entry != null) start(entry) else stopIfIdle()
            else -> stopIfIdle()
        }
        // A killed download is resumed by the user, not restarted by the system
        return START_NOT_STICKY
    }

    /**
     * Android 15+ gives `dataSync` services about 6 hours a day and kills the app if the service
     * outlives it. The download stops at once and keeps its partial file, so the user can resume.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        mTimedOut = true
        mJob?.cancel()
        stopForegroundAndSelf()
    }

    override fun onDestroy() {
        mScope.cancel()
        super.onDestroy()
    }

    private fun start(entry: LocalModelCatalogEntry) {
        // Every startForegroundService call must reach startForeground within a few seconds
        if (mJob?.isActive == true) {
            startInForeground(notification(mShownPercent.takeIf { it >= 0 }))
            return
        }
        mCurrentKey = entry.key
        mShownPercent = -1
        mTimedOut = false
        startInForeground(notification(percent = null))
        mTracker.set(entry.key, LocalModelDownloadState.Running(mDownloader.partialBytes(entry), entry.sizeBytes))
        mJob = mScope.launch {
            try {
                mDownloader.download(
                    entry = entry,
                    onProgress = { downloaded, total -> onProgress(entry, downloaded, total) },
                    onVerifying = { mTracker.set(entry.key, LocalModelDownloadState.Verifying) }
                )
                mTracker.set(entry.key, null)
            } catch (error: CancellationException) {
                val stopped = if (mTimedOut) LocalModelDownloadState.Failed(LocalModelDownloadFailure.TimeLimit) else null
                mTracker.set(entry.key, stopped)
                throw error
            } catch (error: LocalModelDownloadException) {
                mTracker.set(entry.key, LocalModelDownloadState.Failed(error.failure))
            } catch (_: Exception) {
                mTracker.set(entry.key, LocalModelDownloadState.Failed(LocalModelDownloadFailure.Storage))
            } finally {
                mCurrentKey = null
                stopForegroundAndSelf()
            }
        }
    }

    private fun cancel(entry: LocalModelCatalogEntry?) {
        val running = mCurrentKey
        if (entry == null || running != entry.key) {
            stopIfIdle()
            return
        }
        mJob?.cancel()
        // An explicit cancel throws the partial file away; a system kill keeps it for resuming
        mJob?.invokeOnCompletion { mDownloader.discardPartial(entry) }
    }

    private fun onProgress(entry: LocalModelCatalogEntry, downloaded: Long, total: Long) {
        mTracker.set(entry.key, LocalModelDownloadState.Running(downloaded, total))
        val percent = if (total > 0) (downloaded * 100 / total).toInt() else 0
        if (percent == mShownPercent) return
        mShownPercent = percent
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(percent))
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun stopIfIdle() {
        if (mJob?.isActive != true) stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(percent: Int?): Notification {
        ensureChannel()
        val cancelIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, LocalModelDownloadService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_KEY, mCurrentKey),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.local_models_download_notification_title))
            .setProgress(PROGRESS_MAX, percent ?: 0, percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, getString(R.string.local_models_download_cancel), cancelIntent)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.local_models_download_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    companion object {
        private const val ACTION_START = "himitsu.local_models.download.START"
        private const val ACTION_CANCEL = "himitsu.local_models.download.CANCEL"
        private const val EXTRA_KEY = "catalog_key"
        private const val CHANNEL_ID = "file_downloads"
        private const val NOTIFICATION_ID = 4101
        private const val PROGRESS_MAX = 100

        /** Starts downloading [key]; call only after the user confirmed it. */
        fun start(context: Context, key: String) {
            val intent = Intent(context, LocalModelDownloadService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_KEY, key)
            context.startForegroundService(intent)
        }

        /** Cancels the download of [key] and deletes its partial file. */
        fun cancel(context: Context, key: String) {
            val intent = Intent(context, LocalModelDownloadService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_KEY, key)
            context.startService(intent)
        }
    }
}
