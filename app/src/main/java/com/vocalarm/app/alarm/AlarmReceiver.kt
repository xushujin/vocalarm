package com.vocalarm.app.alarm

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vocalarm.app.R
import com.vocalarm.app.VocalarmApp
import com.vocalarm.app.ui.MainActivity

/** Receives the exact alarm PendingIntent and hands the work to the foreground service. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return
        val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        // Record before doing anything that can fail: this is the proof the OS delivered the alarm.
        runCatching {
            (context.applicationContext as VocalarmApp).container.triggerLog.recordDelivered()
        }
        val started = runCatching { AlarmService.start(context, alarmId, snooze) }.isSuccess
        if (!started) {
            postStartFailure(context)
        }
    }

    /** Last resort when the OS refuses a background foreground-service start. */
    private fun postStartFailure(context: Context) {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, NotificationChannels.ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.notification_start_failed_title))
            .setContentText(context.getString(R.string.notification_start_failed_text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        context.getSystemService(android.app.NotificationManager::class.java)
            ?.notify(FAILURE_NOTIFICATION_ID, notification)
    }

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_SNOOZE = "extra_snooze"

        fun createIntent(context: Context, alarmId: String, snooze: Boolean): Intent =
            Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_FIRE
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_SNOOZE, snooze)
            }

        const val ACTION_FIRE = "com.vocalarm.app.action.FIRE_ALARM"
        private const val FAILURE_NOTIFICATION_ID = 1003
    }
}
