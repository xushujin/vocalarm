package com.vocalarm.app.alarm

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vocalarm.app.data.AlarmOccurrenceKind

/** Identity shared by direct system delivery and the broadcast-to-service handoff. */
data class AlarmDelivery(val alarmId: String, val kind: AlarmOccurrenceKind, val token: String) {
    fun serviceIntent(context: Context): Intent = serviceIntent(context, alarmId, kind).apply {
        putExtra(EXTRA_TOKEN, token)
    }

    companion object {
        const val ACTION_DELIVER = "com.vocalarm.app.action.DELIVER_ALARM"
        private const val EXTRA_ALARM_ID = "extra_alarm_id"
        private const val EXTRA_KIND = "extra_occurrence_kind"
        private const val EXTRA_TOKEN = "extra_occurrence_token"

        fun serviceIntent(context: Context, alarmId: String, kind: AlarmOccurrenceKind): Intent =
            Intent(context, AlarmService::class.java).apply {
                action = ACTION_DELIVER
                data = Uri.Builder().scheme("vocalarm").authority("delivery")
                    .appendPath(alarmId).appendPath(kind.name).build()
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_KIND, kind.name)
            }

        fun from(intent: Intent): AlarmDelivery? {
            val id = intent.getStringExtra(EXTRA_ALARM_ID)?.takeIf { it.isNotBlank() } ?: return null
            val kind = intent.getStringExtra(EXTRA_KIND)?.let { raw ->
                AlarmOccurrenceKind.entries.firstOrNull { it.name == raw }
            } ?: return null
            val token = intent.getStringExtra(EXTRA_TOKEN)?.takeIf { it.isNotBlank() } ?: return null
            return AlarmDelivery(id, kind, token)
        }
    }
}
