package com.vocalarm.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vocalarm.app.data.AlarmRepository
import com.vocalarm.app.domain.Alarm
import com.vocalarm.app.domain.AlarmTime
import com.vocalarm.app.ui.MainActivity
import java.time.ZoneId

/**
 * Exact alarm scheduling. `setAlarmClock` is used where possible because it is the only API that
 * survives Doze without throttling and is shown in the system status bar.
 */
class AlarmScheduler(
    private val context: Context,
    private val repository: AlarmRepository,
) {

    private val alarmManager: AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            true
        }

    fun schedule(alarm: Alarm) {
        cancelPending(alarm.id, snooze = false)
        if (!alarm.enabled) {
            cancelPending(alarm.id, snooze = true)
            return
        }
        val triggerAt = AlarmTime.nextTriggerMillis(
            alarm = alarm,
            nowMillis = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
        ) ?: return
        setAlarm(alarm.id, triggerAt, snooze = false)
    }

    fun scheduleSnooze(alarmId: String, minutes: Int) {
        val triggerAt = AlarmTime.snoozeAt(System.currentTimeMillis(), minutes)
        setAlarm(alarmId, triggerAt, snooze = true)
    }

    /**
     * Fires a one-off test ring through the real alarm pipeline (system alarm -> receiver ->
     * service -> full-screen notification) without touching the alarm's own schedule.
     */
    fun scheduleTest(alarmId: String, delayMillis: Long) {
        setAlarm(alarmId, System.currentTimeMillis() + delayMillis, snooze = true)
    }

    fun cancel(id: String) {
        cancelPending(id, snooze = false)
        cancelPending(id, snooze = true)
    }

    fun rescheduleAll() {
        repository.alarms.value.forEach { alarm ->
            if (alarm.enabled) schedule(alarm) else cancel(alarm.id)
        }
    }

    fun cancelAll() {
        repository.alarms.value.forEach { cancel(it.id) }
    }

    private fun setAlarm(alarmId: String, triggerAtMillis: Long, snooze: Boolean) {
        val pending = pendingIntent(alarmId, snooze)
        val exact = canScheduleExact()
        val showIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (exact) {
            runCatching {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                    pending,
                )
            }.recoverCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    private fun cancelPending(alarmId: String, snooze: Boolean) {
        alarmManager.cancel(pendingIntent(alarmId, snooze))
    }

    private fun pendingIntent(alarmId: String, snooze: Boolean): PendingIntent {
        val intent = AlarmReceiver.createIntent(context, alarmId, snooze)
        val requestCode = if (snooze) {
            alarmId.hashCode() xor SNOOZE_REQUEST_MASK
        } else {
            alarmId.hashCode()
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val SNOOZE_REQUEST_MASK = 0x5F5F5F5F
    }
}
