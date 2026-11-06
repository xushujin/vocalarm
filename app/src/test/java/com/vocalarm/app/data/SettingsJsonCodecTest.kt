package com.vocalarm.app.data

import com.vocalarm.app.domain.Alarm
import com.vocalarm.app.domain.LoopMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SettingsJsonCodecTest {

    private fun fixture(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/$name")
        assertNotNull("missing fixture $name", stream)
        return stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun versionOneSettingsGainNewFields() {
        val file = SettingsJsonCodec.decode(fixture("settings_v1.json"))
        assertEquals(1, file.sourceSchema)
        assertEquals("ink", file.settings.paletteId)
        assertEquals(0L, file.settings.lastUpdateCheckAt)
        assertTrue("guardian defaults to on for upgraded installs", file.settings.guardianEnabled)
    }

    @Test
    fun versionTwoSettingsGainGuardianEnabled() {
        val file = SettingsJsonCodec.decode(
            """{"schemaVersion":2,"paletteId":"moss","lastUpdateCheckAt":123}""",
        )
        assertEquals(2, file.sourceSchema)
        assertEquals("moss", file.settings.paletteId)
        assertEquals(123L, file.settings.lastUpdateCheckAt)
        assertTrue(file.settings.guardianEnabled)
    }

    @Test
    fun guardianFlagSurvivesRoundTrip() {
        val settings = AppSettings(paletteId = "mist", guardianEnabled = false)
        val decoded = SettingsJsonCodec.decode(SettingsJsonCodec.encode(settings)).settings
        assertEquals(false, decoded.guardianEnabled)
    }

    @Test
    fun settingsRoundTrip() {
        val settings = AppSettings(paletteId = "moss", lastUpdateCheckAt = 1_760_000_000_000L)
        val decoded = SettingsJsonCodec.decode(SettingsJsonCodec.encode(settings)).settings
        assertEquals(settings, decoded)
    }

    @Test
    fun newerSettingsSchemaIsRejected() {
        try {
            SettingsJsonCodec.decode("""{"schemaVersion":9,"paletteId":"mist"}""")
            fail("expected SchemaTooNewException")
        } catch (expected: SchemaTooNewException) {
            assertEquals(9, expected.found)
        }
    }

    @Test
    fun blankPaletteFallsBackToDefault() {
        assertEquals("mist", SettingsJsonCodec.decode("""{"schemaVersion":2,"paletteId":""}""").settings.paletteId)
    }
}

class RecordingGcTest {

    @Test
    fun unreferencedFilesAreReported() {
        val orphans = RecordingGc.orphans(
            existing = setOf("a.m4a", "b.m4a", "c.m4a"),
            referenced = setOf("b.m4a"),
        )
        assertEquals(listOf("a.m4a", "c.m4a"), orphans)
    }

    @Test
    fun everythingReferencedMeansNoWork() {
        assertTrue(RecordingGc.orphans(setOf("a.m4a"), setOf("a.m4a")).isEmpty())
    }

    @Test
    fun emptyDirectoryIsSafe() {
        assertTrue(RecordingGc.orphans(emptySet(), setOf("a.m4a")).isEmpty())
    }
}

class AlarmNormalizationTest {

    @Test
    fun blankContentAndOutOfRangeValuesAreNormalized() {
        val alarm = Alarm(
            id = "normalize",
            hour = 25,
            minute = 61,
            label = "  " + "x".repeat(80),
            repeatDays = emptySet(),
            text = "   ",
            recordingFile = " ",
            speechRate = 0.1f,
            loopMode = LoopMode.Times,
            loopCount = 0,
            volume = 2f,
            fadeSeconds = -4,
            snoozeMinutes = emptyList(),
        ).normalized()

        assertEquals(23, alarm.hour)
        assertEquals(59, alarm.minute)
        assertEquals(60, alarm.label.length)
        assertEquals(null, alarm.text)
        assertEquals(null, alarm.recordingFile)
        assertEquals(0.5f, alarm.speechRate, 0.001f)
        assertEquals(1, alarm.loopCount)
        assertEquals(1f, alarm.volume, 0.001f)
        assertEquals(0, alarm.fadeSeconds)
        assertEquals(listOf(5, 10), alarm.snoozeMinutes)
        assertEquals(false, alarm.hasPlayableContent)
    }
}
