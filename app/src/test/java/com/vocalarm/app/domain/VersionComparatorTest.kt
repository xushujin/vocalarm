package com.vocalarm.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun newerPatchWins() {
        assertTrue(VersionComparator.isNewer("v1.2.1", "1.2.0"))
        assertFalse(VersionComparator.isNewer("1.2.0", "v1.2.1"))
    }

    @Test
    fun leadingVAndMissingSegmentsAreIgnored() {
        assertEquals(0, VersionComparator.compare("v1.2", "1.2.0"))
        assertEquals(0, VersionComparator.compare("V2.0.0", "2.0.0"))
    }

    @Test
    fun prereleaseIsOlderThanRelease() {
        assertTrue(VersionComparator.isNewer("1.0.0", "1.0.0-beta.2"))
        assertFalse(VersionComparator.isNewer("1.0.0-beta.2", "1.0.0"))
    }

    @Test
    fun prereleaseNumbersCompareNumerically() {
        assertTrue(VersionComparator.isNewer("1.0.0-beta.10", "1.0.0-beta.2"))
    }

    @Test
    fun buildMetadataIsIgnored() {
        assertEquals(0, VersionComparator.compare("1.0.0+build.5", "1.0.0"))
    }

    @Test
    fun majorVersionDominates() {
        assertTrue(VersionComparator.isNewer("2.0.0-alpha", "1.9.9"))
    }

    @Test
    fun normaliseStripsPrefix() {
        assertEquals("1.4.0", VersionComparator.normalize(" v1.4.0 "))
    }
}

class RepeatPlanTest {

    @Test
    fun emptyMeansOnce() {
        assertEquals(RepeatKind.Once, RepeatPlan.kind(emptySet()))
    }

    @Test
    fun allDaysMeansDaily() {
        assertEquals(RepeatKind.Daily, RepeatPlan.kind(java.time.DayOfWeek.values().toSet()))
    }

    @Test
    fun weekdaysAndWeekendsAreDetected() {
        assertEquals(
            RepeatKind.Weekdays,
            RepeatPlan.kind(
                setOf(
                    java.time.DayOfWeek.MONDAY,
                    java.time.DayOfWeek.TUESDAY,
                    java.time.DayOfWeek.WEDNESDAY,
                    java.time.DayOfWeek.THURSDAY,
                    java.time.DayOfWeek.FRIDAY,
                ),
            ),
        )
        assertEquals(
            RepeatKind.Weekends,
            RepeatPlan.kind(setOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY)),
        )
    }

    @Test
    fun mixedDaysAreCustom() {
        assertEquals(
            RepeatKind.Custom,
            RepeatPlan.kind(setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY)),
        )
    }

    @Test
    fun toggleAddsAndRemoves() {
        val day = java.time.DayOfWeek.TUESDAY
        val added = RepeatPlan.toggle(emptySet(), day)
        assertTrue(day in added)
        val removed = RepeatPlan.toggle(added, day)
        assertFalse(day in removed)
    }

    @Test
    fun orderingStartsOnMonday() {
        val ordered = RepeatPlan.ordered(
            setOf(java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.MONDAY),
        )
        assertEquals(listOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.SUNDAY), ordered)
    }
}
