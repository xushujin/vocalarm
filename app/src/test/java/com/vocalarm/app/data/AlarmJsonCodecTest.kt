package com.vocalarm.app.data

import com.vocalarm.app.domain.Alarm
import com.vocalarm.app.domain.LoopMode
import com.vocalarm.app.domain.VoicePreset
import java.time.DayOfWeek
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AlarmJsonCodecTest {

    private fun fixture(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/$name")
        assertNotNull("missing fixture $name", stream)
        return stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun versionOneFileIsMigratedWithExplicitDefaults() {
        val file = AlarmJsonCodec.decode(fixture("alarms_v1.json"))

        assertEquals(1, file.sourceSchema)
        assertEquals(0, file.droppedEntries)
        assertEquals(2, file.alarms.size)

        val weekday = file.alarms.first { it.id == "legacy-weekday" }
        assertEquals(6, weekday.hour)
        assertEquals(45, weekday.minute)
        assertEquals(
            setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            weekday.repeatDays,
        )
        assertEquals("a1b2c3d4-1111-2222-3333-444455556666.m4a", weekday.recordingFile)
        assertNull(weekday.text)
        assertEquals(VoicePreset.Default, weekday.voicePreset)
        assertEquals(1.0f, weekday.speechRate, 0.001f)
        assertEquals(LoopMode.Continuous, weekday.loopMode)
        assertEquals(0.8f, weekday.volume, 0.001f)
        assertFalse(weekday.fadeIn)
        assertEquals(listOf(5, 10), weekday.snoozeMinutes)
        assertTrue(weekday.createdAt > 0L)

        val disabled = file.alarms.first { it.id == "legacy-off" }
        assertFalse(disabled.enabled)
        assertTrue(disabled.repeatDays.isEmpty())
    }

    @Test
    fun versionTwoKeepsTextVoiceAndRate() {
        val file = AlarmJsonCodec.decode(fixture("alarms_v2.json"))
        assertEquals(2, file.sourceSchema)
        val alarm = file.alarms.single()
        assertEquals("早上好，今天记得带伞。", alarm.text)
        assertEquals("zh-cn-x-ccc-local", alarm.voiceName)
        assertEquals(1.2f, alarm.speechRate, 0.001f)
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), alarm.repeatDays)
        // v2 -> v3 defaults
        assertEquals(LoopMode.Continuous, alarm.loopMode)
        assertEquals(listOf(5, 10), alarm.snoozeMinutes)
    }

    @Test
    fun encodeDecodeRoundTripPreservesAllFields() {
        val original = Alarm(
            id = "round-trip",
            hour = 21,
            minute = 7,
            label = "晚间提醒",
            enabled = true,
            repeatDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
            recordingFile = "voice.m4a",
            text = "记得关窗。",
            voicePreset = VoicePreset.Custom,
            voiceName = "zh-cn-x-ccc-local",
            speechRate = 1.4f,
            loopMode = LoopMode.Times,
            loopCount = 4,
            volume = 0.65f,
            fadeIn = true,
            fadeSeconds = 12,
            snoozeMinutes = listOf(5, 10),
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_100_000L,
        )

        val encoded = AlarmJsonCodec.encode(listOf(original))
        val decoded = AlarmJsonCodec.decode(encoded).alarms.single()
        assertEquals(original, decoded)

        val root = JSONObject(encoded)
        assertEquals(AlarmJsonCodec.CURRENT_SCHEMA, root.getInt("schemaVersion"))
    }

    @Test
    fun newerSchemaIsRejectedWithoutDataLoss() {
        val raw = """{"schemaVersion":99,"alarms":[]}"""
        try {
            AlarmJsonCodec.decode(raw)
            fail("expected SchemaTooNewException")
        } catch (expected: SchemaTooNewException) {
            assertEquals(99, expected.found)
            assertEquals(AlarmJsonCodec.CURRENT_SCHEMA, expected.supported)
        }
    }

    @Test
    fun brokenEntriesAreDroppedButValidOnesSurvive() {
        val raw = """
            {
              "schemaVersion": 3,
              "alarms": [
                {"id": "ok", "hour": 7, "minute": 0},
                {"hour": 8, "minute": 0},
                {"id": "also-ok", "hour": 9, "minute": 30, "unknownField": "ignored"}
              ]
            }
        """.trimIndent()
        val file = AlarmJsonCodec.decode(raw)
        assertEquals(2, file.alarms.size)
        assertEquals(1, file.droppedEntries)
        assertTrue(file.alarms.any { it.id == "also-ok" })
    }

    @Test
    fun valuesAreClampedDuringDecode() {
        val raw = """
            {
              "schemaVersion": 3,
              "alarms": [
                {
                  "id": "clamp",
                  "hour": 99,
                  "minute": -3,
                  "speechRate": 9.5,
                  "loopCount": 999,
                  "volume": 0.0,
                  "fadeSeconds": 500,
                  "snoozeMinutes": [0, 5, 99, 10]
                }
              ]
            }
        """.trimIndent()
        val alarm = AlarmJsonCodec.decode(raw).alarms.single()
        assertEquals(23, alarm.hour)
        assertEquals(0, alarm.minute)
        assertEquals(2.0f, alarm.speechRate, 0.001f)
        assertEquals(20, alarm.loopCount)
        assertEquals(0.05f, alarm.volume, 0.001f)
        assertEquals(60, alarm.fadeSeconds)
        assertEquals(listOf(5, 10), alarm.snoozeMinutes)
    }
}
