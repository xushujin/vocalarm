package com.vocalarm.app.alarm

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TriggerLogTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun upgradingOldLogDoesNotClaimTheScreenWasShown() {
        val file = File(temporaryFolder.root, "trigger.log")
        file.writeText("deliveredAt=100\ndeliveredCount=6\nringingAt=110\n")

        assertEquals(TriggerLog.Snapshot(100, 6, 110, 0), TriggerLog(file).read())
    }

    @Test
    fun deliveryAndRingingAreNotEvidenceOfAVisibleScreen() {
        val file = File(temporaryFolder.root, "trigger.log")
        TriggerLog(file).recordDelivered(100)
        TriggerLog(file).recordRinging(110)

        assertEquals(TriggerLog.Snapshot(100, 1, 110, 0, ringingCount = 1), TriggerLog(file).read())
        TriggerLog(file).recordScreenShown(120)
        assertEquals(TriggerLog.Snapshot(100, 1, 110, 120, ringingCount = 1), TriggerLog(file).read())
    }

    @Test
    fun laterDeliveryKeepsPreviousScreenTimestampSoFailedLaunchCanBeDetected() {
        val file = File(temporaryFolder.root, "trigger.log")
        val log = TriggerLog(file)
        log.recordDelivered(100)
        log.recordRinging(110)
        log.recordScreenShown(120)
        log.recordDelivered(200)
        log.recordRinging(210)

        assertEquals(TriggerLog.Snapshot(200, 2, 210, 120, ringingCount = 2), TriggerLog(file).read())
    }

    @Test
    fun broadcastAndServiceCountAsOneDeliveryAndKeepTheFirstTimestampAfterReload() {
        val file = File(temporaryFolder.root, "trigger.log")
        TriggerLog(file).recordDelivered(100, "first", 90, TriggerLog.DeliverySource.Broadcast)
        TriggerLog(file).recordDelivered(120, "first", 90, TriggerLog.DeliverySource.Service)
        val snapshot = TriggerLog(file).read()

        assertEquals(1, snapshot.deliveredCount)
        assertEquals(100L, snapshot.lastDeliveredAt)
        assertEquals(90L, snapshot.lastScheduledAt)
        assertEquals(TriggerLog.DeliverySource.Broadcast, snapshot.lastDeliverySource)
        TriggerLog(file).recordDelivered(200, "second", 190, TriggerLog.DeliverySource.Service)
        assertEquals(2, TriggerLog(file).read().deliveredCount)
    }

    @Test
    fun lateDeliveryDoesNotEraseEvidenceThatOpeningTheAppRecoveredIt() {
        val file = File(temporaryFolder.root, "trigger.log")
        TriggerLog(file).recordAppOpened(200)
        TriggerLog(file).recordRecovery(100, "AppOpened", 210)
        TriggerLog(file).recordDelivered(220, "late", 100, TriggerLog.DeliverySource.Broadcast)
        TriggerLog(file).recordRinging(230)

        val snapshot = TriggerLog(file).read()
        assertEquals(100L, snapshot.lastScheduledAt)
        assertEquals(220L, snapshot.lastDeliveredAt)
        assertEquals(210L, snapshot.lastRecoveryAt)
        assertEquals(100L, snapshot.lastRecoveredScheduledAt)
        assertEquals("AppOpened", snapshot.lastRecoverySource)
        assertEquals(200L, snapshot.lastAppOpenedAt)
    }

    @Test
    fun restoringFutureAlarmIsNotReportedAsMissed() {
        val file = File(temporaryFolder.root, "trigger.log")
        TriggerLog(file).recordRecovery(300, "AppOpened", 100)

        assertEquals(0L, TriggerLog(file).read().lastRecoveryAt)
    }

    @Test
    fun jobAfterLauncherDoesNotReplaceTheOriginalRecoveryCause() {
        val file = File(temporaryFolder.root, "trigger.log")
        TriggerLog(file).recordRecovery(100, "AppOpened", 200, "overdue")
        TriggerLog(file).recordRecovery(100, "Watchdog", 210, "overdue")

        val snapshot = TriggerLog(file).read()
        assertEquals("AppOpened", snapshot.lastRecoverySource)
        assertEquals(200L, snapshot.lastRecoveryAt)
        TriggerLog(file).recordRecovery(300, "Watchdog", 400, "another")
        assertEquals("Watchdog", TriggerLog(file).read().lastRecoverySource)
        assertEquals(300L, TriggerLog(file).read().lastRecoveredScheduledAt)
    }

    @Test
    fun deniedServiceStartCanBeReportedWithoutLosingDeliveryEvidence() {
        val file = File(temporaryFolder.root, "trigger.log")
        TriggerLog(file).recordDelivered(100, "first", 90, TriggerLog.DeliverySource.Broadcast)
        TriggerLog(file).recordFailure("ringingServiceStart", IllegalStateException("blocked\nby system"), 110)

        val snapshot = TriggerLog(file).read()
        assertEquals(100L, snapshot.lastDeliveredAt)
        assertEquals(110L, snapshot.lastFailureAt)
        assertEquals("ringingServiceStart: IllegalStateException: blocked by system", snapshot.lastFailure)
    }
}
