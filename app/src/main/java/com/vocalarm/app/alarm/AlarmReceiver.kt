package com.vocalarm.app.alarm

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vocalarm.app.R
import com.vocalarm.app.VocalarmApp
import com.vocalarm.app.data.AlarmOccurrenceKind
import com.vocalarm.app.ui.MainActivity

/** Receives the exact alarm PendingIntent and hands the work to the foreground service. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { raw ->
            runCatching { AlarmOccurrenceKind.valueOf(raw) }.getOrNull()
        } ?: return
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
        // Record before doing anything that can fail: this is the proof the OS delivered the alarm.
        runCatching {
            (context.applicationContext as VocalarmApp).container.triggerLog.recordDelivered()
        }
        val started = runCatching {
            val scheduler = (context.applicationContext as VocalarmApp).container.scheduler
            if (!scheduler.consume(alarmId, kind, token)) return
            AlarmService.start(context, alarmId, kind != AlarmOccurrenceKind.Regular)
        }.isSuccess
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
        private const val EXTRA_KIND = "extra_occurrence_kind"
        private const val EXTRA_TOKEN = "extra_occurrence_token"

        fun createIntent(context: Context, alarmId: String, kind: AlarmOccurrenceKind, token: String?): Intent =
            Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_FIRE
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
    }
}
