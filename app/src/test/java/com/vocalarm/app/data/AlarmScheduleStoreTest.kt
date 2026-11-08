package com.vocalarm.app.data

import com.vocalarm.app.domain.Alarm
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AlarmScheduleStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val alarm = Alarm(id = "wake", hour = 7, minute = 0)
    private val deadline = at(7, 0)

    private fun at(hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 11, 8, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun file(): File = File(temporaryFolder.root, "schedules.json")

    private fun store(): AlarmScheduleStore = AlarmScheduleStore(JsonStore(file()))

    @Test
    fun coldStartRestoresOriginalDeadlineAndDeliveryToken() {
        val occurrence = AlarmOccurrence(alarm.id, AlarmOccurrenceKind.Regular, deadline)
        store().put(occurrence)

        val restored = store().restore(listOf(alarm), at(6, 59), zone)

        assertEquals(listOf(occurrence), restored.pending)
    }

    @Test
    fun maintenanceJustAfterDueTimeDoesNotMoveAlarmToTomorrow() {
        val occurrence = AlarmOccurrence(alarm.id, AlarmOccurrenceKind.Regular, deadline)
        val schedule = store()
        schedule.put(occurrence)

        repeat(3) {
            assertEquals(listOf(occurrence), schedule.restore(listOf(alarm), at(7, 2), zone).pending)
        }
    }

    @Test
    fun snoozeSurvivesRestartAfterOneShotWasDisabled() {
        val snooze = AlarmOccurrence(alarm.id, AlarmOccurrenceKind.Snooze, at(7, 5))
        store().put(snooze)

        val restored = store().restore(listOf(alarm.copy(enabled = false)), at(7, 2), zone)

        assertEquals(listOf(snooze), restored.pending)
    }

    @Test
    fun recentlyOverdueSnoozeStillRunsAfterColdStart() {
        val snooze = AlarmOccurrence(alarm.id, AlarmOccurrenceKind.Snooze, at(7, 5))
        store().put(snooze)

        assertEquals(
            listOf(snooze),
            store().restore(listOf(alarm.copy(enabled = false)), at(7, 6), zone).pending,
        )
    }

    @Test
    fun consumedOccurrenceCannotBeDeliveredAgainAfterProcessRestart() {
        val occurrence = AlarmOccurrence(alarm.id, AlarmOccurrenceKind.Regular, deadline)
        val schedule = store()
        schedule.put(occurrence)

        assertTrue(schedule.consume(alarm.id, occurrence.kind, occurrence.token, deadline))
        assertFalse(store().consume(alarm.id, occurrence.kind, occurrence.token, deadline))
    }

    @Test
    fun consumedOneShotCannotReappearIfDisabledDefinitionWriteWasLost() {
        val occurrence = AlarmOccurrence(alarm.id, AlarmOccurrenceKind.Regular, deadline)
        val schedule = store()
        schedule.put(occurrence)
        schedule.consume(alarm.id, occurrence.kind, occurrence.token, deadline)

        val restored = store().restore(listOf(alarm), at(7, 1), zone)

        assertTrue(restored.pending.isEmpty())
        assertEquals(setOf(alarm.id), restored.finishedOneShots)
    }

    @Test
    fun consumedRepeatingAlarmRecoversFollowingOccurrenceIfProcessDies() {
        val recurring = alarm.copy(repeatDays = DayOfWeek.entries.toSet())
        val occurrence = AlarmOccurrence(alarm.id, AlarmOccurrenceKind.Regular, deadline)
        val schedule = store()
        schedule.put(occurrence)
        schedule.consume(alarm.id, occurrence.kind, occurrence.token, deadline)

        val restored = store().restore(listOf(recurring), at(7, 1), zone)

        assertEquals(deadline + 24 * 60 * 60 * 1000L, restored.pending.single().triggerAtMillis)
        assertTrue(restored.finishedOneShots.isEmpty())
    }

    @Test
    fun veryOldOneShotIsRetiredInsteadOfRingingHoursLaterOrTomorrow() {
        store().put(AlarmOccurrence(alarm.id, AlarmOccurrenceKind.Regular, deadline))

        val restored = store().restore(listOf(alarm), at(12, 0), zone)

        assertTrue(restored.pending.isEmpty())
        assertEquals(setOf(alarm.id), restored.finishedOneShots)
        assertTrue(store().restore(listOf(alarm), at(12, 1), zone).pending.isEmpty())
    }

    @Test
    fun veryOldSnoozeExpiresWithoutReactivatingItsDisabledParent() {
        store().put(AlarmOccurrence(alarm.id, AlarmOccurrenceKind.Snooze, at(7, 5)))

        val restored = store().restore(listOf(alarm.copy(enabled = false)), at(12, 0), zone)

        assertTrue(restored.pending.isEmpty())
        assertNull(store().find(alarm.id, AlarmOccurrenceKind.Snooze))
    }

    @Test
    fun diagnosticTestDoesNotOverwriteSnooze() {
        val snooze = AlarmOccurrence(alarm.id, AlarmOccurrenceKind.Snooze, at(7, 5))
        val test = AlarmOccurrence(alarm.id, AlarmOccurrenceKind.Test, at(7, 1))
        val schedule = store()
        schedule.put(snooze)
        schedule.put(test)

        assertEquals(
            setOf(snooze, test),
            store().restore(listOf(alarm.copy(enabled = false)), at(7, 0), zone).pending.toSet(),
        )
    }

    @Test
    fun staleDeliveryCannotConsumeReplacementAlarm() {
        val previous = AlarmOccurrence(alarm.id, AlarmOccurrenceKind.Regular, deadline)
        val replacement = AlarmOccurrence(alarm.id, AlarmOccurrenceKind.Regular, at(8, 0))
        val schedule = store()
        schedule.put(previous)
        schedule.put(replacement)

        assertFalse(schedule.consume(alarm.id, previous.kind, previous.token, deadline))
        assertEquals(replacement, store().find(alarm.id, AlarmOccurrenceKind.Regular))
    }

    @Test
    fun delayedIntentWithUpdatedTokenCannotConsumeFutureReplacement() {
        val replacement = AlarmOccurrence(alarm.id, AlarmOccurrenceKind.Regular, at(8, 0))
        val schedule = store()
        schedule.put(replacement)

        assertFalse(schedule.consume(alarm.id, replacement.kind, replacement.token, at(7, 0)))
        assertEquals(replacement, store().find(alarm.id, replacement.kind))
        assertTrue(schedule.consume(alarm.id, replacement.kind, replacement.token, at(8, 0)))
    }

    @Test
    fun explicitCancellationRemovesRegularSnoozeAndTestFromDisk() {
        val schedule = store()
        AlarmOccurrenceKind.entries.forEach { schedule.put(AlarmOccurrence(alarm.id, it, deadline)) }

        schedule.cancel(alarm.id)

        AlarmOccurrenceKind.entries.forEach { assertNull(store().find(alarm.id, it)) }
    }

    @Test
    fun deletedAlarmCannotBeRestoredFromOccurrenceFile() {
        val schedule = store()
        AlarmOccurrenceKind.entries.forEach { schedule.put(AlarmOccurrence(alarm.id, it, deadline)) }

        assertTrue(schedule.restore(emptyList(), at(6, 0), zone).pending.isEmpty())
        AlarmOccurrenceKind.entries.forEach { assertNull(store().find(alarm.id, it)) }
    }

    @Test
    fun failedConsumptionWriteKeepsPendingOccurrenceInMemoryAndOnDisk() {
        val occurrence = AlarmOccurrence(alarm.id, AlarmOccurrenceKind.Regular, deadline)
        val schedule = store()
        schedule.put(occurrence)
        // A directory at the temporary-file path deterministically prevents an atomic write.
        assertTrue(File(temporaryFolder.root, "schedules.json.tmp").mkdir())

        assertTrue(runCatching { schedule.consume(alarm.id, occurrence.kind, occurrence.token, deadline) }.isFailure)

        assertEquals(occurrence, schedule.find(alarm.id, occurrence.kind))
        assertEquals(occurrence, store().find(alarm.id, occurrence.kind))
    }

    @Test
    fun newerSchemaIsPreservedAndCannotBeOverwrittenByRestoration() {
        val raw = """{"schemaVersion":2,"futureField":"keep me","occurrences":[]}"""
        file().writeText(raw)
        val schedule = store()

        val result = runCatching { schedule.restore(listOf(alarm), at(6, 0), zone) }

        assertTrue(result.exceptionOrNull() is SchemaTooNewException)
        assertEquals(raw, file().readText())
        assertEquals(listOf("schedules.json"), temporaryFolder.root.listFiles()!!.map { it.name })
    }
}
