package com.vocalarm.app.data

import com.vocalarm.app.domain.Alarm
import com.vocalarm.app.domain.StoreIssue
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for alarms. The in-memory list is updated immediately so the UI stays
 * responsive, while the file write happens on a serialised IO coroutine.
 */
class AlarmRepository(
    private val store: JsonStore,
    private val scope: CoroutineScope,
) {

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _issue = MutableStateFlow<StoreIssue?>(null)
    val issue: StateFlow<StoreIssue?> = _issue.asStateFlow()

    private val _readOnly = MutableStateFlow(false)
    val readOnly: StateFlow<Boolean> = _readOnly.asStateFlow()

    private val writeMutex = Mutex()

    init {
        load()
    }

    private fun load() {
        val raw = store.read() ?: return
        runCatching { AlarmJsonCodec.decode(raw) }
            .onSuccess { file ->
                _alarms.value = file.alarms
                if (file.droppedEntries > 0) {
                    _issue.value = StoreIssue.DroppedEntries(file.droppedEntries)
                }
            }
            .onFailure { error ->
                if (error is SchemaTooNewException) {
                    // Never touch a file written by a newer build; the user may downgrade and keep data.
                    _readOnly.value = true
                    _issue.value = StoreIssue.SchemaTooNew(error.found, error.supported)
                } else {
                    val backup = store.moveToBackup("corrupt")
                    _issue.value = StoreIssue.Corrupt(backup?.name)
                }
            }
    }

    fun find(id: String): Alarm? = _alarms.value.firstOrNull { it.id == id }

    fun save(alarm: Alarm): Alarm {
        val now = System.currentTimeMillis()
        val normalized = alarm.normalized().let { candidate ->
            candidate.copy(
                id = candidate.id.ifBlank { UUID.randomUUID().toString() },
                createdAt = if (candidate.createdAt == 0L) now else candidate.createdAt,
                updatedAt = now,
            )
        }
        _alarms.update { current ->
            val index = current.indexOfFirst { it.id == normalized.id }
            if (index >= 0) {
                current.toMutableList().apply { set(index, normalized) }
            } else {
                current + normalized
            }.sortedWith(compareBy({ it.hour * 60 + it.minute }, { it.createdAt }))
        }
        persist()
        return normalized
    }

    fun delete(id: String): Alarm? {
        val removed = find(id) ?: return null
        _alarms.update { current -> current.filterNot { it.id == id } }
        persist()
        return removed
    }

    fun setEnabled(id: String, enabled: Boolean): Alarm? {
        val alarm = find(id) ?: return null
        val updated = alarm.copy(enabled = enabled, updatedAt = System.currentTimeMillis())
        _alarms.update { current -> current.map { if (it.id == id) updated else it } }
        persist()
        return updated
    }

    fun clearIssue() {
        _issue.value = null
    }

    private fun persist() {
        if (_readOnly.value) return
        scope.launch(Dispatchers.IO) {
            writeMutex.withLock {
                val snapshot = _alarms.value
                runCatching { store.write(AlarmJsonCodec.encode(snapshot)) }
                    .onFailure { error ->
                        _issue.value = StoreIssue.SaveFailed(error.message ?: error.javaClass.simpleName)
                    }
            }
        }
    }
}

/** Keeps recording files in one place and prevents path traversal from a stale JSON value. */
class RecordingStore(private val root: File) {

    val directory: File
        get() = root.apply { if (!exists()) mkdirs() }

    fun newFile(): File = File(directory, "${UUID.randomUUID()}.$EXTENSION")

    fun fileFor(name: String?): File? {
        if (name == null || !NAME_PATTERN.matches(name)) return null
        return File(directory, name)
    }

    fun existingNames(): Set<String> = directory.listFiles()
        ?.filter { it.isFile && it.extension == EXTENSION }
        ?.mapNotNull { it.name }
        ?.toSet()
        .orEmpty()

    fun delete(name: String?): Boolean {
        val file = fileFor(name) ?: return false
        return file.isFile && file.delete()
    }

    /** Deletes recordings no alarm references any more, and reports what was removed. */
    fun cleanupOrphans(alarms: List<Alarm>): List<String> {
        val referenced = alarms.mapNotNull { it.recordingFile }.toSet()
        val orphans = RecordingGc.orphans(existingNames(), referenced)
        orphans.forEach { delete(it) }
        return orphans
    }

    companion object {
        const val EXTENSION = "m4a"
        private val NAME_PATTERN = Regex("^[A-Za-z0-9_-]{1,64}\\.$EXTENSION$")
    }
}

/** Pure helper so the garbage-collection rule can be unit tested without a filesystem. */
object RecordingGc {
    fun orphans(existing: Set<String>, referenced: Set<String>): List<String> =
        existing.filterNot { it in referenced }.sorted()
}
