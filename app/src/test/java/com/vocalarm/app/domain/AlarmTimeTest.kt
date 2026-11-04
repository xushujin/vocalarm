package com.vocalarm.app.domain

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmTimeTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone)

    private fun alarm(
        hour: Int,
        minute: Int,
        days: Set<java.time.DayOfWeek> = emptySet(),
        enabled: Boolean = true,
    ) = Alarm(
        id = "test",
        hour = hour,
        minute = minute,
        enabled = enabled,
        repeatDays = days,
    )

    @Test
    fun dailyAlarmKeepsSameDayWhenTimeIsAhead() {
        val next = AlarmTime.nextTrigger(
            alarm(7, 30, java.time.DayOfWeek.values().toSet()),
            at(2026, 11, 4, 6, 0),
        )
        assertEquals(at(2026, 11, 4, 7, 30), next)
    }

    @Test
    fun dailyAlarmMovesToTomorrowWhenTimePassed() {
        val next = AlarmTime.nextTrigger(
            alarm(7, 30, java.time.DayOfWeek.values().toSet()),
            at(2026, 11, 4, 8, 0),
        )
        assertEquals(at(2026, 11, 5, 7, 30), next)
    }

    @Test
    fun oneShotAlarmIsTomorrowWhenTimeAlreadyPassed() {
        val next = AlarmTime.nextTrigger(alarm(9, 0), at(2026, 11, 4, 12, 0))
        assertEquals(at(2026, 11, 5, 9, 0), next)
    }

    @Test
    fun weekdaysSkipWeekend() {
        // 2026-11-06 is a Friday, so the next weekday 08:00 is Monday 2026-11-09.
        val weekdays = setOf(
            java.time.DayOfWeek.MONDAY,
            java.time.DayOfWeek.TUESDAY,
            java.time.DayOfWeek.WEDNESDAY,
            java.time.DayOfWeek.THURSDAY,
            java.time.DayOfWeek.FRIDAY,
        )
        val next = AlarmTime.nextTrigger(
            alarm(8, 0, weekdays),
            at(2026, 11, 6, 9, 0),
        )
        assertEquals(at(2026, 11, 9, 8, 0), next)
    }

    @Test
    fun weekendAlarmFiresOnSaturday() {
        val weekends = setOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY)
        val next = AlarmTime.nextTrigger(
            alarm(8, 0, weekends),
            at(2026, 11, 6, 9, 0),
        )
        assertEquals(at(2026, 11, 7, 8, 0), next)
    }

    @Test
    fun customDaysPickTheNearestMatchingDay() {
        val days = setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY)
        val next = AlarmTime.nextTrigger(
            alarm(6, 15, days),
            at(2026, 11, 4, 10, 0),
        )
        // 2026-11-04 is a Wednesday but 06:15 already passed, so Monday 2026-11-09.
        assertEquals(at(2026, 11, 9, 6, 15), next)
    }

    @Test
    fun disabledAlarmHasNoTrigger() {
        assertNull(AlarmTime.nextTrigger(alarm(7, 0, enabled = false), at(2026, 11, 4, 12, 0)))
    }

    @Test
    fun triggersAreStrictlyInTheFuture() {
        val next = AlarmTime.nextTrigger(
            alarm(7, 0, java.time.DayOfWeek.values().toSet()),
            at(2026, 11, 4, 7, 0),
        )
        assertEquals(at(2026, 11, 5, 7, 0), next)
        assertTrue(next!!.toInstant().toEpochMilli() > at(2026, 11, 4, 7, 0).toInstant().toEpochMilli())
    }

    @Test
    fun nextEnabledTriggerPicksEarliestAlarm() {
        val alarms = listOf(
            alarm(9, 0, java.time.DayOfWeek.values().toSet()),
            alarm(7, 30, java.time.DayOfWeek.values().toSet()),
            alarm(6, 0, java.time.DayOfWeek.values().toSet(), enabled = false),
        )
        val now = at(2026, 11, 4, 6, 0).toInstant().toEpochMilli()
        val next = AlarmTime.nextEnabledTriggerMillis(alarms, now, zone)
        assertEquals(at(2026, 11, 4, 7, 30).toInstant().toEpochMilli(), next)
    }

    @Test
    fun remainingRoundsUpPartialMinutes() {
        val remaining = Remaining.of(nowMillis = 0L, triggerAtMillis = 61_000L)
        assertEquals(0L, remaining.hours)
        assertEquals(2L, remaining.minutes)
    }
}
