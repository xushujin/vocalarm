package com.vocalarm.app.data

import com.vocalarm.app.domain.Alarm
import com.vocalarm.app.domain.StoreIssue
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AlarmRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    // No deferred write can run: simulates losing the process before its IO coroutine executes.
    private val stoppedScope = CoroutineScope(Job().apply { cancel() } + Dispatchers.Unconfined)

    @Test
    fun flushMakesNewAlarmAvailableToColdStartedReceiver() {
        val file = File(temporaryFolder.root, "alarms.json")
        val repository = repository(file)
        val saved = repository.save(Alarm(id = "wake", hour = 7, minute = 30))

        assertFalse(file.exists())
        assertTrue(repository.flush())

        assertEquals(saved, repository(file).find(saved.id))
    }

    @Test
    fun flushCommitsLatestEnabledStateBeforeScheduling() {
        val file = File(temporaryFolder.root, "alarms.json")
        val repository = repository(file)
        val saved = repository.save(Alarm(id = "wake", hour = 7, minute = 30))
        repository.setEnabled(saved.id, false)
        repository.setEnabled(saved.id, true)

        assertTrue(repository.flush())
        assertEquals(repository.find(saved.id), repository(file).find(saved.id))
    }

    @Test
    fun failedCommitReportsFailureSoSchedulerCanAvoidAnUnusableAlarm() {
        val parentFile = temporaryFolder.newFile("not-a-directory")
        val repository = repository(File(parentFile, "alarms.json"))
        repository.save(Alarm(id = "wake", hour = 7, minute = 30))

        assertFalse(repository.flush())
        assertTrue(repository.issue.value is StoreIssue.SaveFailed)
    }

    @Test
    fun flushDoesNotOverwriteNewerSchema() {
        val file = temporaryFolder.newFile("alarms.json")
        val futureJson = """{"schemaVersion":999,"alarms":[]}"""
        file.writeText(futureJson)

        assertFalse(repository(file).flush())
        assertEquals(futureJson, file.readText())
    }

    @Test
    fun deletionSurvivesImmediateProcessDeathWithoutDeferredWrites() {
        val file = File(temporaryFolder.root, "alarms.json")
        val repository = repository(file)
        val saved = repository.save(Alarm(id = "wake", hour = 7, minute = 30))
        assertTrue(repository.flush())

        assertEquals(saved, repository.delete(saved.id))

        assertNull(repository(file).find(saved.id))
    }

    private fun repository(file: File) = AlarmRepository(JsonStore(file), stoppedScope)
}
