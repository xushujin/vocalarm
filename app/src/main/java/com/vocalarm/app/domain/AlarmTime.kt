package com.vocalarm.app.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure trigger calculation. Kept free of Android APIs so it can be unit tested and reused by both
 * the scheduler and the UI ("next alarm in ...").
 */
object AlarmTime {

    fun nextTrigger(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
        if (!alarm.enabled) return null
        val time = LocalTime.of(alarm.hour.coerceIn(0, 23), alarm.minute.coerceIn(0, 59))
        val zone = now.zone

        if (alarm.repeatDays.isEmpty()) {
            val today = now.toLocalDate().atTime(time).atZone(zone)
            return if (today.toInstant().isAfter(now.toInstant())) {
                today
            } else {
                now.toLocalDate().plusDays(1).atTime(time).atZone(zone)
            }
        }

        // A week is enough to cover every repeat combination; the +1 day guards the "same time
        // today already passed" case.
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (date.dayOfWeek !in alarm.repeatDays) continue
            val candidate = date.atTime(time).atZone(zone)
            if (candidate.toInstant().isAfter(now.toInstant())) return candidate
        }
        return null
    }

    fun nextTriggerMillis(alarm: Alarm, nowMillis: Long, zone: ZoneId): Long? {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        return nextTrigger(alarm, now)?.toInstant()?.toEpochMilli()
    }

    /** Snooze is always a one-shot offset, independent of the repeat rule. */
    fun snoozeAt(nowMillis: Long, minutes: Int): Long = nowMillis + minutes.coerceIn(1, 60) * 60_000L

    fun nextEnabledTriggerMillis(alarms: List<Alarm>, nowMillis: Long, zone: ZoneId): Long? =
        alarms.asSequence()
            .filter { it.enabled }
            .mapNotNull { nextTriggerMillis(it, nowMillis, zone) }
            .minOrNull()
}

/** Hours and minutes left, rounded up so "0 minutes" never appears while an alarm is pending. */
data class RemainingTime(val hours: Long, val minutes: Long)

object Remaining {
    fun of(triggerAtMillis: Long, nowMillis: Long): RemainingTime {
        val diff = (triggerAtMillis - nowMillis).coerceAtLeast(0L)
        val totalMinutes = (diff + 59_999L) / 60_000L
        return RemainingTime(hours = totalMinutes / 60, minutes = totalMinutes % 60)
    }
}
