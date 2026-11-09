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

        assertEquals(TriggerLog.Snapshot(100, 1, 110, 0), TriggerLog(file).read())
        TriggerLog(file).recordScreenShown(120)
        assertEquals(TriggerLog.Snapshot(100, 1, 110, 120), TriggerLog(file).read())
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

        assertEquals(TriggerLog.Snapshot(200, 2, 210, 120), TriggerLog(file).read())
    }
}
