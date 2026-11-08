package com.vocalarm.app.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocalarm.app.alarm.AlarmScheduler
import com.vocalarm.app.data.AlarmRepository
import com.vocalarm.app.data.RecordingStore
import com.vocalarm.app.domain.Alarm
import com.vocalarm.app.domain.AlarmTime
import com.vocalarm.app.domain.StoreIssue
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AlarmListState(
    val alarms: List<Alarm> = emptyList(),
    val issue: StoreIssue? = null,
    val readOnly: Boolean = false,
    val now: Long = System.currentTimeMillis(),
    val nextAlarm: Alarm? = null,
    val nextTriggerAt: Long? = null,
)

class AlarmListViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val recordings: RecordingStore,
) : ViewModel() {

    private val now = MutableStateFlow(System.currentTimeMillis())

    val state: StateFlow<AlarmListState> = combine(
        repository.alarms,
        repository.issue,
        repository.readOnly,
        now,
    ) { alarms, issue, readOnly, nowMillis ->
        val zone = ZoneId.systemDefault()
        val upcoming = alarms
            .filter { it.enabled }
            .mapNotNull { alarm ->
                AlarmTime.nextTriggerMillis(alarm, nowMillis, zone)?.let { alarm to it }
            }
            .minByOrNull { it.second }
        AlarmListState(
            alarms = alarms,
            issue = issue,
            readOnly = readOnly,
            now = nowMillis,
            nextAlarm = upcoming?.first,
            nextTriggerAt = upcoming?.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlarmListState(),
    )

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                now.value = System.currentTimeMillis()
            }
        }
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean) {
        val updated = repository.setEnabled(alarm.id, enabled) ?: return
        scheduler.schedule(updated)
    }

    fun delete(alarm: Alarm) {
        repository.delete(alarm.id) ?: return
        scheduler.cancel(alarm.id)
        recordings.delete(alarm.recordingFile)
        recordings.cleanupOrphans(repository.alarms.value)
    }

    fun clearIssue() {
        repository.clearIssue()
    }
}
