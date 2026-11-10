package com.vocalarm.app.alarm

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.vocalarm.app.R
import com.vocalarm.app.VocalarmApp
import com.vocalarm.app.data.AlarmOccurrenceKind
import com.vocalarm.app.ui.MainActivity

/** Primary alarm delivery path; starts the ringing service inside the OS alarm grace window. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val delivery = AlarmDelivery.from(intent) ?: return
        val container = (context.applicationContext as VocalarmApp).container
        val occurrence = container.scheduler.pendingOccurrence(delivery.alarmId, delivery.kind, delivery.token) ?: return
        container.triggerLog.recordDelivered(
            token = delivery.token,
            scheduledAt = occurrence.triggerAtMillis,
            source = TriggerLog.DeliverySource.Broadcast,
        )
        val started = runCatching {
            ContextCompat.startForegroundService(context, delivery.serviceIntent(context))
        }.onFailure { container.triggerLog.recordFailure("ringingServiceStart", it) }.isSuccess
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
        // Do not alert on the ringing channel while the independent service fallback is pending.
        val notification = Notification.Builder(context, NotificationChannels.ALARM_STATUS)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.notification_start_failed_title))
            .setContentText(context.getString(R.string.notification_start_failed_text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        context.getSystemService(android.app.NotificationManager::class.java)
            ?.notify(FAILURE_NOTIFICATION_ID, notification)
    }

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_SNOOZE = "extra_snooze"
        private const val EXTRA_KIND = "extra_occurrence_kind"
        private const val EXTRA_TOKEN = "extra_occurrence_token"

        fun createIntent(context: Context, alarmId: String, kind: AlarmOccurrenceKind, token: String?): Intent =
            Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_FIRE
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                // Extras are not part of PendingIntent identity; the full id avoids hash collisions.
                data = Uri.Builder().scheme("vocalarm").authority("alarm")
                    .appendPath(alarmId).appendPath(kind.name).build()
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_SNOOZE, kind != AlarmOccurrenceKind.Regular)
                putExtra(EXTRA_KIND, kind.name)
                putExtra(EXTRA_TOKEN, token)
            }

        const val ACTION_FIRE = "com.vocalarm.app.action.FIRE_ALARM"
        private const val FAILURE_NOTIFICATION_ID = 1003

        fun clearStartFailure(context: Context) {
            context.getSystemService(android.app.NotificationManager::class.java)?.cancel(FAILURE_NOTIFICATION_ID)
        }
    }
}
