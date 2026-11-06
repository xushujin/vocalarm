package com.vocalarm.app.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.vocalarm.app.R
import com.vocalarm.app.VocalarmApp
import com.vocalarm.app.ui.MainActivity

/**
 * Optional always-on companion service.
 *
 * A task swipe kills the app process on most ROMs, and several vendor ROMs go further and drop the
 * package's alarms with it. Keeping a foreground service alive makes the process survive the swipe,
 * and re-arming from [onTaskRemoved] covers the ROMs that remove the alarms anyway.
 */
class GuardianService : Service() {

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The task is gone but we are still alive: put every enabled alarm back into AlarmManager.
        runCatching {
            (application as VocalarmApp).container.scheduler.rescheduleAll()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val content = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, NotificationChannels.ALARM_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(getString(R.string.guardian_title))
            .setContentText(getString(R.string.guardian_text))
            .setContentIntent(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1010

        /** Best-effort view for the self check; the OS is the real source of truth. */
        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, GuardianService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, GuardianService::class.java)) }
        }
    }
}
