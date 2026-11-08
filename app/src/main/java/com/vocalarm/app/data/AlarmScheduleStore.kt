package com.vocalarm.app.data

import com.vocalarm.app.domain.Alarm
import com.vocalarm.app.domain.AlarmTime
import java.time.ZoneId
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class AlarmOccurrenceKind { Regular, Snooze, Test }

data class AlarmOccurrence(
    val alarmId: String,
    val kind: AlarmOccurrenceKind,
    val triggerAtMillis: Long,
    val token: String = UUID.randomUUID().toString(),
    val consumed: Boolean = false,
)

/** Durable occurrence state; an alarm's enabled flag alone cannot describe a pending snooze. */
class AlarmScheduleStore(private val store: JsonStore) {

    private var unsupportedSchema: SchemaTooNewException? = null
    private var occurrences: List<AlarmOccurrence> = load()

    fun find(alarmId: String, kind: AlarmOccurrenceKind): AlarmOccurrence? =
        occurrences.firstOrNull { it.alarmId == alarmId && it.kind == kind }

    fun put(occurrence: AlarmOccurrence) {
        replace(occurrences.filterNot { it.alarmId == occurrence.alarmId && it.kind == occurrence.kind } + occurrence)
    }

    fun cancel(alarmId: String) {
        replace(occurrences.filterNot { it.alarmId == alarmId })
    }

    /** Persist before allowing playback, so duplicate deliveries after a cold start are harmless. */
    fun consume(alarmId: String, kind: AlarmOccurrenceKind, token: String, nowMillis: Long): Boolean {
        val occurrence = find(alarmId, kind) ?: return false
        if (occurrence.consumed || occurrence.token != token || nowMillis < occurrence.triggerAtMillis) return false
        put(occurrence.copy(consumed = true))
        return true
    }

    data class Restoration(
        val pending: List<AlarmOccurrence>,
        val finishedOneShots: Set<String>,
    )

    /**
     * Restore the original deadline, including a recently overdue alarm. Never advance an
     * unconsumed occurrence merely because a watchdog or process startup happened after its time.
     * Thirty minutes matches the maximum ringing session; older missed alarms are retired.
     */
    fun restore(alarms: List<Alarm>, nowMillis: Long, zone: ZoneId): Restoration {
        val byId = alarms.associateBy { it.id }
        val next = occurrences.filter { it.alarmId in byId }.toMutableList()
        val finished = mutableSetOf<String>()
        alarms.forEach { alarm ->
            val index = next.indexOfFirst { it.alarmId == alarm.id && it.kind == AlarmOccurrenceKind.Regular }
            var regular = next.getOrNull(index)
            if (!alarm.enabled) {
                if (index >= 0 && regular?.consumed == false) next.removeAt(index)
                return@forEach
            }
            if (regular != null && nowMillis - regular.triggerAtMillis > RECOVERY_GRACE_MS) {
                regular = regular.copy(consumed = true)
                next[index] = regular
            }
            if (regular?.consumed == true && alarm.repeatDays.isEmpty()) {
                finished += alarm.id
            } else if (regular == null || regular.consumed) {
                AlarmTime.nextTriggerMillis(alarm, nowMillis, zone)?.let { deadline ->
                    val occurrence = AlarmOccurrence(alarm.id, AlarmOccurrenceKind.Regular, deadline)
                    if (index >= 0) next[index] = occurrence else next += occurrence
                }
            }
        }
        // Disabled one-shot definitions still own their snoozes and diagnostic test alarms.
        val restored = next.filterNot {
            it.kind != AlarmOccurrenceKind.Regular &&
                (it.consumed || nowMillis - it.triggerAtMillis > RECOVERY_GRACE_MS)
        }
        replace(restored)
        return Restoration(restored.filterNot { it.consumed }, finished)
    }

    private fun replace(next: List<AlarmOccurrence>) {
        unsupportedSchema?.let { throw it }
        if (next == occurrences) return
        val array = JSONArray()
        next.forEach { occurrence ->
            array.put(JSONObject().apply {
                put("alarmId", occurrence.alarmId)
                put("kind", occurrence.kind.name)
                put("triggerAtMillis", occurrence.triggerAtMillis)
                put("token", occurrence.token)
                put("consumed", occurrence.consumed)
            })
        }
        store.write(JSONObject().put("schemaVersion", 1).put("occurrences", array).toString())
        occurrences = next
    }

    private fun load(): List<AlarmOccurrence> {
        val raw = store.read() ?: return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            val version = root.getInt("schemaVersion")
            if (version > 1) throw SchemaTooNewException(version, 1)
            require(version == 1)
            val array = root.getJSONArray("occurrences")
            (0 until array.length()).map { index ->
                val entry = array.getJSONObject(index)
                AlarmOccurrence(
                    alarmId = entry.getString("alarmId"),
                    kind = AlarmOccurrenceKind.valueOf(entry.getString("kind")),
                    triggerAtMillis = entry.getLong("triggerAtMillis"),
                    token = entry.getString("token"),
                    consumed = entry.optBoolean("consumed", false),
                )
            }
        }.getOrElse { error ->
            if (error is SchemaTooNewException) {
                unsupportedSchema = error
            } else {
                store.moveToBackup("corrupt")
            }
            emptyList()
        }
    }

    companion object {
        const val RECOVERY_GRACE_MS = 30 * 60 * 1000L
    }
}
