package com.vocalarm.app.data

import com.vocalarm.app.domain.StoreIssue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class AppSettings(
    val paletteId: String = "mist",
    val lastUpdateCheckAt: Long = 0L,
    val guardianEnabled: Boolean = true,
)

data class SettingsFile(
    val settings: AppSettings,
    val sourceSchema: Int,
)

object SettingsJsonCodec {

    const val CURRENT_SCHEMA = 3

    private const val KEY_SCHEMA = "schemaVersion"
    private const val KEY_PALETTE = "paletteId"
    private const val KEY_LAST_CHECK = "lastUpdateCheckAt"
    private const val KEY_GUARDIAN = "guardianEnabled"

    fun decode(raw: String): SettingsFile {
        val root = JSONObject(raw)
        val source = if (root.has(KEY_SCHEMA)) root.optInt(KEY_SCHEMA, 1) else 1
        var version = source.coerceAtLeast(1)
        if (version > CURRENT_SCHEMA) throw SchemaTooNewException(version, CURRENT_SCHEMA)
        if (version < 2) {
            if (!root.has(KEY_LAST_CHECK)) root.put(KEY_LAST_CHECK, 0L)
            version = 2
            root.put(KEY_SCHEMA, version)
        }
        if (version < 3) {
            // v3 adds the background guardian; existing installs get it enabled by default.
            if (!root.has(KEY_GUARDIAN)) root.put(KEY_GUARDIAN, true)
            version = 3
            root.put(KEY_SCHEMA, version)
        }
        return SettingsFile(
            settings = AppSettings(
                paletteId = root.optString(KEY_PALETTE, "mist").ifBlank { "mist" },
                lastUpdateCheckAt = root.optLong(KEY_LAST_CHECK, 0L),
                guardianEnabled = root.optBoolean(KEY_GUARDIAN, true),
            ),
            sourceSchema = source,
        )
    }

    fun encode(settings: AppSettings): String = JSONObject().apply {
        put(KEY_SCHEMA, CURRENT_SCHEMA)
        put(KEY_PALETTE, settings.paletteId)
        put(KEY_LAST_CHECK, settings.lastUpdateCheckAt)
        put(KEY_GUARDIAN, settings.guardianEnabled)
    }.toString(2)
}

class SettingsRepository(
    private val store: JsonStore,
    private val scope: CoroutineScope,
) {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _issue = MutableStateFlow<StoreIssue?>(null)
    val issue: StateFlow<StoreIssue?> = _issue.asStateFlow()

    private var readOnly = false
    private val writeMutex = Mutex()

    init {
        load()
    }

    private fun load() {
        val raw = store.read() ?: return
        runCatching { SettingsJsonCodec.decode(raw) }
            .onSuccess { file -> _settings.value = file.settings }
            .onFailure { error ->
                if (error is SchemaTooNewException) {
                    readOnly = true
                    _issue.value = StoreIssue.SchemaTooNew(error.found, error.supported)
                } else {
                    val backup = store.moveToBackup("corrupt")
                    _issue.value = StoreIssue.Corrupt(backup?.name)
                }
            }
    }

    fun setPalette(paletteId: String) {
        if (_settings.value.paletteId == paletteId) return
        _settings.update { it.copy(paletteId = paletteId) }
        persist()
    }

    fun markUpdateChecked(timestamp: Long) {
        _settings.update { it.copy(lastUpdateCheckAt = timestamp) }
        persist()
    }

    fun setGuardianEnabled(enabled: Boolean) {
        if (_settings.value.guardianEnabled == enabled) return
        _settings.update { it.copy(guardianEnabled = enabled) }
        persist()
    }

    private fun persist() {
        if (readOnly) return
        scope.launch(Dispatchers.IO) {
            writeMutex.withLock {
                runCatching { store.write(SettingsJsonCodec.encode(_settings.value)) }
                    .onFailure { error ->
                        _issue.value = StoreIssue.SaveFailed(error.message ?: error.javaClass.simpleName)
                    }
            }
        }
    }
}
