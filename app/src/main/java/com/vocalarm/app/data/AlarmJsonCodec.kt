package com.vocalarm.app.data

import com.vocalarm.app.domain.Alarm
import com.vocalarm.app.domain.LoopMode
import com.vocalarm.app.domain.VoicePreset
import java.time.DayOfWeek
import org.json.JSONArray
import org.json.JSONObject

class SchemaTooNewException(val found: Int, val supported: Int) :
    Exception("Alarm file schema $found is newer than supported $supported")

data class AlarmFile(
    val alarms: List<Alarm>,
    val sourceSchema: Int,
    val droppedEntries: Int,
)

/**
 * Explicit, forward-only migrations. Every schema change gets a new version and a migration step;
 * no code path recreates or truncates the file when the version is unknown.
 */
object AlarmJsonCodec {

    const val CURRENT_SCHEMA = 3

    private const val KEY_SCHEMA = "schemaVersion"
    private const val KEY_ALARMS = "alarms"

    private const val F_ID = "id"
    private const val F_HOUR = "hour"
    private const val F_MINUTE = "minute"
    private const val F_LABEL = "label"
    private const val F_ENABLED = "enabled"
    private const val F_REPEAT = "repeatDays"
    private const val F_RECORDING = "recording"
    private const val F_TEXT = "text"
    private const val F_VOICE_PRESET = "voicePreset"
    private const val F_VOICE_NAME = "voiceName"
    private const val F_RATE = "speechRate"
    private const val F_LOOP_MODE = "loopMode"
    private const val F_LOOP_COUNT = "loopCount"
    private const val F_VOLUME = "volume"
    private const val F_FADE_IN = "fadeIn"
    private const val F_FADE_SECONDS = "fadeSeconds"
    private const val F_SNOOZE = "snoozeMinutes"
    private const val F_CREATED = "createdAt"
    private const val F_UPDATED = "updatedAt"

    fun decode(raw: String): AlarmFile {
        val root = JSONObject(raw)
        val sourceSchema = if (root.has(KEY_SCHEMA)) root.optInt(KEY_SCHEMA, 1) else 1
        var version = sourceSchema.coerceAtLeast(1)
        if (version > CURRENT_SCHEMA) {
            throw SchemaTooNewException(found = version, supported = CURRENT_SCHEMA)
        }
        while (version < CURRENT_SCHEMA) {
            when (version) {
                1 -> migrateV1ToV2(root)
                2 -> migrateV2ToV3(root)
                else -> error("No migration registered for schema $version")
            }
            version += 1
            root.put(KEY_SCHEMA, version)
        }

        val array = root.optJSONArray(KEY_ALARMS) ?: JSONArray()
        val alarms = ArrayList<Alarm>(array.length())
        var dropped = 0
        for (index in 0 until array.length()) {
            val parsed = array.optJSONObject(index)?.let { parseAlarm(it) }
            if (parsed == null) {
                dropped += 1
            } else {
                alarms += parsed
            }
        }
        return AlarmFile(
            alarms = alarms.sortedWith(compareBy({ it.hour * 60 + it.minute }, { it.createdAt })),
            sourceSchema = sourceSchema,
            droppedEntries = dropped,
        )
    }

    fun encode(alarms: List<Alarm>): String {
        val root = JSONObject()
        root.put(KEY_SCHEMA, CURRENT_SCHEMA)
        val array = JSONArray()
        alarms.forEach { alarm -> array.put(encodeAlarm(alarm)) }
        root.put(KEY_ALARMS, array)
        return root.toString(2)
    }

    private fun encodeAlarm(alarm: Alarm): JSONObject = JSONObject().apply {
        put(F_ID, alarm.id)
        put(F_HOUR, alarm.hour)
        put(F_MINUTE, alarm.minute)
        put(F_LABEL, alarm.label)
        put(F_ENABLED, alarm.enabled)
        put(F_REPEAT, JSONArray(alarm.repeatDays.sortedBy { it.value }.map { it.value }))
        put(F_RECORDING, alarm.recordingFile ?: JSONObject.NULL)
        put(F_TEXT, alarm.text ?: JSONObject.NULL)
        put(F_VOICE_PRESET, alarm.voicePreset.storageValue())
        put(F_VOICE_NAME, alarm.voiceName ?: JSONObject.NULL)
        put(F_RATE, alarm.speechRate.toDouble())
        put(F_LOOP_MODE, alarm.loopMode.storageValue())
        put(F_LOOP_COUNT, alarm.loopCount)
        put(F_VOLUME, alarm.volume.toDouble())
        put(F_FADE_IN, alarm.fadeIn)
        put(F_FADE_SECONDS, alarm.fadeSeconds)
        put(F_SNOOZE, JSONArray(alarm.snoozeMinutes))
        put(F_CREATED, alarm.createdAt)
        put(F_UPDATED, alarm.updatedAt)
    }

    private fun parseAlarm(obj: JSONObject): Alarm? {
        val id = obj.optString(F_ID).takeIf { it.isNotBlank() } ?: return null
        val repeatArray = obj.optJSONArray(F_REPEAT)
        val days = mutableSetOf<DayOfWeek>()
        if (repeatArray != null) {
            for (index in 0 until repeatArray.length()) {
                val value = repeatArray.optInt(index, -1)
                if (value in 1..7) days += DayOfWeek.of(value)
            }
        }
        val snoozeArray = obj.optJSONArray(F_SNOOZE)
        val snooze = if (snoozeArray == null) {
            listOf(5, 10)
        } else {
            (0 until snoozeArray.length()).map { snoozeArray.optInt(it, 5) }
        }
        return Alarm(
            id = id,
            hour = obj.optInt(F_HOUR, 7).coerceIn(0, 23),
            minute = obj.optInt(F_MINUTE, 0).coerceIn(0, 59),
            label = obj.optString(F_LABEL, ""),
            enabled = obj.optBoolean(F_ENABLED, true),
            repeatDays = days,
            recordingFile = obj.stringOrNull(F_RECORDING),
            text = obj.stringOrNull(F_TEXT),
            voicePreset = parseVoicePreset(obj.stringOrNull(F_VOICE_PRESET)),
            voiceName = obj.stringOrNull(F_VOICE_NAME),
            speechRate = obj.optDouble(F_RATE, 1.0).toFloat(),
            loopMode = parseLoopMode(obj.stringOrNull(F_LOOP_MODE)),
            loopCount = obj.optInt(F_LOOP_COUNT, 3),
            volume = obj.optDouble(F_VOLUME, 0.8).toFloat(),
            fadeIn = obj.optBoolean(F_FADE_IN, false),
            fadeSeconds = obj.optInt(F_FADE_SECONDS, 10),
            snoozeMinutes = snooze,
            createdAt = obj.optLong(F_CREATED, 0L),
            updatedAt = obj.optLong(F_UPDATED, 0L),
        ).normalized()
    }

    private fun migrateV1ToV2(root: JSONObject) {
        forEachAlarm(root) { alarm ->
            if (!alarm.has(F_TEXT)) alarm.put(F_TEXT, JSONObject.NULL)
            if (!alarm.has(F_VOICE_NAME)) alarm.put(F_VOICE_NAME, JSONObject.NULL)
            if (!alarm.has(F_RATE)) alarm.put(F_RATE, 1.0)
        }
    }

    private fun migrateV2ToV3(root: JSONObject) {
        val now = System.currentTimeMillis()
        forEachAlarm(root) { alarm ->
            if (!alarm.has(F_LABEL)) alarm.put(F_LABEL, "")
            if (!alarm.has(F_VOICE_PRESET)) alarm.put(F_VOICE_PRESET, "auto")
            if (!alarm.has(F_LOOP_MODE)) alarm.put(F_LOOP_MODE, "continuous")
            if (!alarm.has(F_LOOP_COUNT)) alarm.put(F_LOOP_COUNT, 3)
            if (!alarm.has(F_VOLUME)) alarm.put(F_VOLUME, 0.8)
            if (!alarm.has(F_FADE_IN)) alarm.put(F_FADE_IN, false)
            if (!alarm.has(F_FADE_SECONDS)) alarm.put(F_FADE_SECONDS, 10)
            if (!alarm.has(F_SNOOZE)) {
                alarm.put(F_SNOOZE, JSONArray().put(5).put(10))
            }
            if (!alarm.has(F_CREATED)) alarm.put(F_CREATED, now)
            if (!alarm.has(F_UPDATED)) alarm.put(F_UPDATED, now)
        }
    }

    private fun forEachAlarm(root: JSONObject, block: (JSONObject) -> Unit) {
        val array = root.optJSONArray(KEY_ALARMS) ?: return
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let(block)
        }
    }

    private fun JSONObject.stringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun VoicePreset.storageValue(): String = when (this) {
        VoicePreset.Default -> "auto"
        VoicePreset.Female -> "female"
        VoicePreset.Male -> "male"
        VoicePreset.Custom -> "custom"
    }

    private fun parseVoicePreset(raw: String?): VoicePreset = when (raw?.lowercase()) {
        "female" -> VoicePreset.Female
        "male" -> VoicePreset.Male
        "custom" -> VoicePreset.Custom
        else -> VoicePreset.Default
    }

    private fun LoopMode.storageValue(): String = when (this) {
        LoopMode.Once -> "once"
        LoopMode.Times -> "times"
        LoopMode.Continuous -> "continuous"
    }

    private fun parseLoopMode(raw: String?): LoopMode = when (raw?.lowercase()) {
        "once" -> LoopMode.Once
        "times" -> LoopMode.Times
        else -> LoopMode.Continuous
    }
}
