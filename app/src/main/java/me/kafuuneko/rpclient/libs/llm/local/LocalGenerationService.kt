// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.disguise.isDisguiseActive
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps the process in the foreground while a local model writes a reply.
 *
 * A process holding several GB in the background is the first one Android kills, so leaving the
 * app mid-reply would lose it. A `shortService` (Android 14+) needs no special permission and
 * lasts about three minutes, enough for a reply. On older versions nothing is started.
 * The notification text is neutral and names neither the app's purpose nor the model.
 */
class LocalGenerationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            stopSelf()
            return START_NOT_STICKY
        }
        // startForeground must always run once the service was started as a foreground service
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        )
        LocalGenerationKeeper.isForeground = true
        // The reply may have finished before the service got here; then it stops itself
        if (!LocalGenerationKeeper.isGenerating) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        LocalGenerationKeeper.isForeground = false
        super.onDestroy()
    }

    /** The time limit of a short service ended: the reply goes on only while the app is visible. */
    override fun onTimeout(startId: Int) {
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.local_inference_generation_channel),
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.local_inference_generation_notification))
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "background_work"
        const val NOTIFICATION_ID = 4102
    }
}

/**
 * Starts [LocalGenerationService] when the first local generation begins and stops it when the
 * last one ends. Failing to start (for example when the app is already in the background) is not
 * an error: the reply just loses that protection. While disguise is on nothing is started, so
 * no notification names the app; a reply then goes on only while the app is visible.
 */
class LocalGenerationKeeper(private val mContext: Context) {
    fun begin() {
        if (sActive.getAndIncrement() != 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        // feat/disguise: the notification header and the task manager would show the app name
        if (isDisguiseActive()) return
        runCatching { mContext.startForegroundService(Intent(mContext, LocalGenerationService::class.java)) }
    }

    fun end() {
        if (sActive.decrementAndGet() != 0) return
        // Stopping before startForeground ran would crash; the service stops itself in that case
        if (isForeground) mContext.stopService(Intent(mContext, LocalGenerationService::class.java))
    }

    companion object {
        private val sActive = AtomicInteger()

        /** Set by the service once it runs in the foreground. */
        @Volatile
        internal var isForeground = false

        internal val isGenerating: Boolean
            get() = sActive.get() > 0
    }
}
