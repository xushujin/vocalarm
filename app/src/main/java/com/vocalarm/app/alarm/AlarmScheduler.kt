package com.vocalarm.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vocalarm.app.data.AlarmRepository
import com.vocalarm.app.data.AlarmOccurrence
import com.vocalarm.app.data.AlarmOccurrenceKind
import com.vocalarm.app.data.AlarmScheduleStore
import com.vocalarm.app.data.JsonStore
import com.vocalarm.app.domain.Alarm
import com.vocalarm.app.domain.AlarmTime
import com.vocalarm.app.ui.MainActivity
import java.io.File
import java.time.ZoneId

/**
 * Uses `setAlarmClock` where possible so the system can wake from Doze and expose the next alarm.
 */
class AlarmScheduler(
    private val context: Context,
    private val repository: AlarmRepository,
    private val triggerLog: TriggerLog,
) {

    enum class RestoreSource { AppOpened, BootOrClockChange, TaskRemoved, Watchdog, ExpiredDelivery }

    private val alarmManager: AlarmManager =
        context.getSystemService(AlarmManager::class.java)
    private val scheduleStore = AlarmScheduleStore(JsonStore(File(context.filesDir, "alarm-schedules.json")))

    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            true
        }

    /** Includes pending snoozes and tests, even when their one-shot definition is disabled. */
    fun nextScheduledAt(): Long? = scheduleStore.pending()
        .filter { occurrence ->
            repository.find(occurrence.alarmId)?.let { alarm ->
                alarm.enabled || occurrence.kind != AlarmOccurrenceKind.Regular
            } == true
        }
        .minOfOrNull { it.triggerAtMillis }

    fun schedule(alarm: Alarm): Boolean {
        if (!repository.flush()) return false
        return safely { scheduleRegular(alarm) }
    }

    private fun scheduleRegular(alarm: Alarm) {
        if (!alarm.enabled) {
            cancelStored(alarm.id)
            return
        }
        val triggerAt = AlarmTime.nextTriggerMillis(
            alarm = alarm,
            nowMillis = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
        ) ?: return
        register(AlarmOccurrence(alarm.id, AlarmOccurrenceKind.Regular, triggerAt))
    }

    fun scheduleSnooze(alarmId: String, minutes: Int): Boolean {
        if (!repository.flush()) return false
        val triggerAt = AlarmTime.snoozeAt(System.currentTimeMillis(), minutes)
        return safely { register(AlarmOccurrence(alarmId, AlarmOccurrenceKind.Snooze, triggerAt)) }
    }

    /**
     * Exercises the same broadcast and service deliveries as a regular alarm without changing it.
     */
    fun scheduleTest(alarmId: String, delayMillis: Long): Boolean {
        if (!repository.flush()) return false
        return safely {
            register(AlarmOccurrence(alarmId, AlarmOccurrenceKind.Test, System.currentTimeMillis() + delayMillis))
        }
    }

    fun cancel(id: String): Boolean = safely { cancelStored(id) }

    private fun cancelStored(id: String) {
        scheduleStore.cancel(id)
        AlarmOccurrenceKind.entries.forEach { cancelPending(id, it) }
        cancelLegacyPending(id)
    }

    fun rescheduleAll(source: RestoreSource, recalculate: Boolean = false): Boolean = safely {
        val alarms = repository.alarms.value
        val now = System.currentTimeMillis()
        if (recalculate) {
            alarms.filter { it.enabled }.forEach(::scheduleRegular)
        }
        val restored = scheduleStore.restore(alarms, now, ZoneId.systemDefault())
        alarms.forEach { alarm ->
            cancelLegacyPending(alarm.id)
            AlarmOccurrenceKind.entries.forEach { kind ->
                if (restored.pending.none { it.alarmId == alarm.id && it.kind == kind }) {
                    cancelPending(alarm.id, kind)
                }
            }
        }
        restored.finishedOneShots.forEach { repository.setEnabled(it, false) }
        restored.pending.forEach(::setAlarm)
        restored.pending.filter { it.triggerAtMillis < now }.minByOrNull { it.triggerAtMillis }?.let {
            triggerLog.recordRecovery(it.triggerAtMillis, source.name, now, it.token)
        }
    }

    /** A read-only check; the receiver must never consume before the service actually starts. */
    fun pendingOccurrence(alarmId: String, kind: AlarmOccurrenceKind, token: String): AlarmOccurrence? {
        val alarm = repository.find(alarmId) ?: return null
        if (kind == AlarmOccurrenceKind.Regular && !alarm.enabled) return null
        return scheduleStore.find(alarmId, kind)?.takeIf { it.token == token && !it.consumed }
    }

    /** The delivered service claims each persisted occurrence once before starting playback. */
    fun consume(alarmId: String, kind: AlarmOccurrenceKind, token: String): Boolean {
        val alarm = repository.find(alarmId) ?: return false
        if (kind == AlarmOccurrenceKind.Regular && !alarm.enabled) return false
        val occurrence = pendingOccurrence(alarmId, kind, token) ?: return false
        val nowMillis = System.currentTimeMillis()
        if (nowMillis < occurrence.triggerAtMillis) return false
        if (nowMillis - occurrence.triggerAtMillis > AlarmScheduleStore.RECOVERY_GRACE_MS) {
            rescheduleAll(RestoreSource.ExpiredDelivery)
            return false
        }
        val consumed = runCatching { scheduleStore.consume(alarmId, kind, token, nowMillis) }
            .onFailure(repository::reportSchedulingFailure)
            .getOrDefault(false)
        if (!consumed) return false
        safely { cancelPending(alarmId, kind) }
        if (kind == AlarmOccurrenceKind.Regular) {
            if (alarm.repeatDays.isEmpty()) {
                repository.setEnabled(alarmId, false)
            } else {
                // A crash while starting audio must not lose the following occurrence.
                safely { scheduleRegular(alarm) }
            }
        }
        return true
    }

    fun cancelAll() {
        repository.alarms.value.forEach { cancel(it.id) }
    }

    private fun register(occurrence: AlarmOccurrence) {
        scheduleStore.put(occurrence)
        cancelLegacyPending(occurrence.alarmId)
        setAlarm(occurrence)
    }

    private inline fun safely(block: () -> Unit): Boolean =
        runCatching(block).onFailure {
            repository.reportSchedulingFailure(it)
            triggerLog.recordFailure("schedule", it)
        }.isSuccess

    private fun setAlarm(occurrence: AlarmOccurrence) {
        // Keep two independent OS-owned delivery paths. Neither bypasses force-stop or vendor
        // auto-start restrictions; the service claims their shared token once before playback.
        val broadcastPending = PendingIntent.getBroadcast(
            context,
            0,
            AlarmReceiver.createIntent(context, occurrence.alarmId, occurrence.kind, occurrence.token),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val servicePending = PendingIntent.getForegroundService(
            context,
            0,
            AlarmDelivery(occurrence.alarmId, occurrence.kind, occurrence.token).serviceIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
                    AlarmManager.AlarmClockInfo(occurrence.triggerAtMillis, showIntent),
                    broadcastPending,
                )
            }.recoverCatching {
                // Preserve exact delivery if a ROM refuses setAlarmClock.
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, occurrence.triggerAtMillis, broadcastPending)
            }.getOrThrow()
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, occurrence.triggerAtMillis, broadcastPending)
        }
        // The primary alarm is already registered. Failure to add the optional fallback must not
        // misreport that primary registration as a failure or interrupt restoration of other alarms.
        runCatching {
            val fallbackAt = occurrence.triggerAtMillis + SERVICE_FALLBACK_DELAY_MS
            if (exact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fallbackAt, servicePending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fallbackAt, servicePending)
            }
        }.onFailure { triggerLog.recordFailure("serviceFallback", it) }
    }

    private fun cancelPending(alarmId: String, kind: AlarmOccurrenceKind) {
        // Looking up a token must never update its extras: an in-flight delivery may still read it.
        val servicePending = PendingIntent.getForegroundService(
            context,
            0,
            AlarmDelivery.serviceIntent(context, alarmId, kind),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (servicePending != null) {
            alarmManager.cancel(servicePending)
            servicePending.cancel()
        }
        val broadcastPending = PendingIntent.getBroadcast(
            context,
            0,
            AlarmReceiver.createIntent(context, alarmId, kind, null),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (broadcastPending != null) {
            alarmManager.cancel(broadcastPending)
            broadcastPending.cancel()
        }
    }

    /** Replace registrations made before occurrence tokens and unique intent URIs were introduced. */
    private fun cancelLegacyPending(alarmId: String) {
        listOf(alarmId.hashCode(), alarmId.hashCode() xor SNOOZE_REQUEST_MASK).forEach { requestCode ->
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
    }

    private companion object {
        const val SNOOZE_REQUEST_MASK = 0x5F5F5F5F
        const val SERVICE_FALLBACK_DELAY_MS = 2_000L
    }
}
