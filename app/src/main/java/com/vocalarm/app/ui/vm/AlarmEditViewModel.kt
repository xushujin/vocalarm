package com.vocalarm.app.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocalarm.app.alarm.AlarmScheduler
import com.vocalarm.app.audio.AudioPlayer
import com.vocalarm.app.audio.PreviewState
import com.vocalarm.app.audio.TtsEngine
import com.vocalarm.app.audio.VoiceRecorder
import com.vocalarm.app.data.AlarmRepository
import com.vocalarm.app.data.RecordingStore
import com.vocalarm.app.domain.Alarm
import com.vocalarm.app.domain.LoopMode
import com.vocalarm.app.domain.RepeatPlan
import com.vocalarm.app.domain.VoiceDescriptor
import com.vocalarm.app.domain.VoicePreset
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlarmDraft(
    val id: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val recordingFile: String? = null,
    val text: String = "",
    val voicePreset: VoicePreset = VoicePreset.Default,
    val voiceName: String? = null,
    val speechRate: Float = 1.0f,
    val loopMode: LoopMode = LoopMode.Continuous,
    val loopCount: Int = 3,
    val volume: Float = 0.8f,
    val fadeIn: Boolean = true,
    val fadeSeconds: Int = 10,
    val snoozeMinutes: List<Int> = listOf(5, 10),
    val createdAt: Long = 0L,
) {
    fun toAlarm(): Alarm = Alarm(
        id = id,
        hour = hour,
        minute = minute,
        label = label,
        enabled = enabled,
        repeatDays = repeatDays,
        recordingFile = recordingFile,
        text = text.trim().ifEmpty { null },
        voicePreset = voicePreset,
        voiceName = voiceName,
        speechRate = speechRate,
        loopMode = loopMode,
        loopCount = loopCount,
        volume = volume,
        fadeIn = fadeIn,
        fadeSeconds = fadeSeconds,
        snoozeMinutes = snoozeMinutes,
        createdAt = createdAt,
    )
}

class AlarmEditViewModel(
    alarmId: String?,
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val recordings: RecordingStore,
    private val recorder: VoiceRecorder,
    private val player: AudioPlayer,
    private val tts: TtsEngine,
) : ViewModel() {

    private val original: Alarm? = alarmId?.let(repository::find)
    private val originalRecording: String? = original?.recordingFile

    private val _draft = MutableStateFlow(initialDraft(original))
    val draft: StateFlow<AlarmDraft> = _draft.asStateFlow()

    val recordingState: StateFlow<VoiceRecorder.RecordState> = recorder.state
    val preview: StateFlow<PreviewState> = player.preview
    val previewError: StateFlow<String?> = player.previewError
    val ttsStatus: StateFlow<TtsEngine.Status> = tts.status
    val ttsFailure: StateFlow<TtsEngine.Failure?> = tts.failure
    val voices: StateFlow<List<VoiceDescriptor>> = tts.voices

    private val _previewingSpeech = MutableStateFlow(false)
    val previewingSpeech: StateFlow<Boolean> = _previewingSpeech.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val isNewAlarm: Boolean = original == null

    private fun initialDraft(alarm: Alarm?): AlarmDraft {
        if (alarm == null) {
            val start = LocalTime.now().plusHours(1)
            return AlarmDraft(
                hour = start.hour,
                minute = start.minute,
                createdAt = System.currentTimeMillis(),
            )
        }
        return AlarmDraft(
            id = alarm.id,
            hour = alarm.hour,
            minute = alarm.minute,
            label = alarm.label,
            enabled = alarm.enabled,
            repeatDays = alarm.repeatDays,
            recordingFile = alarm.recordingFile,
            text = alarm.text.orEmpty(),
            voicePreset = alarm.voicePreset,
            voiceName = alarm.voiceName,
            speechRate = alarm.speechRate,
            loopMode = alarm.loopMode,
            loopCount = alarm.loopCount,
            volume = alarm.volume,
            fadeIn = alarm.fadeIn,
            fadeSeconds = alarm.fadeSeconds,
            snoozeMinutes = alarm.snoozeMinutes,
            createdAt = alarm.createdAt,
        )
    }

    fun setHour(hour: Int) {
        _draft.update { it.copy(hour = hour.coerceIn(0, 23)) }
    }

    fun setMinute(minute: Int) {
        _draft.update { it.copy(minute = minute.coerceIn(0, 59)) }
    }

    fun setLabel(label: String) {
        _draft.update { it.copy(label = label.take(60)) }
    }

    fun toggleDay(day: DayOfWeek) {
        _draft.update { it.copy(repeatDays = RepeatPlan.toggle(it.repeatDays, day)) }
    }

    fun setRepeatPreset(kind: com.vocalarm.app.domain.RepeatKind) {
        val days = when (kind) {
            com.vocalarm.app.domain.RepeatKind.Once -> emptySet()
            com.vocalarm.app.domain.RepeatKind.Daily -> DayOfWeek.values().toSet()
            com.vocalarm.app.domain.RepeatKind.Weekdays -> setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            )

            com.vocalarm.app.domain.RepeatKind.Weekends -> setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            com.vocalarm.app.domain.RepeatKind.Custom -> _draft.value.repeatDays
        }
        _draft.update { it.copy(repeatDays = days) }
    }

    fun setText(text: String) {
        if (text.length > MAX_TEXT_LENGTH) {
            _message.value = "text-too-long"
            _draft.update { it.copy(text = text.take(MAX_TEXT_LENGTH)) }
        } else {
            _draft.update { it.copy(text = text) }
        }
    }

    fun setVoicePreset(preset: VoicePreset) {
        _draft.update { it.copy(voicePreset = preset, voiceName = if (preset == VoicePreset.Custom) it.voiceName else null) }
    }

    fun setCustomVoice(name: String) {
        _draft.update { it.copy(voicePreset = VoicePreset.Custom, voiceName = name) }
    }

    fun setSpeechRate(rate: Float) {
        _draft.update { it.copy(speechRate = rate.coerceIn(Alarm.MIN_SPEECH_RATE, Alarm.MAX_SPEECH_RATE)) }
    }

    fun setLoopMode(mode: LoopMode) {
        _draft.update { it.copy(loopMode = mode) }
    }

    fun setLoopCount(count: Int) {
        _draft.update { it.copy(loopCount = count.coerceIn(1, Alarm.MAX_LOOP_COUNT)) }
    }

    fun setVolume(volume: Float) {
        _draft.update { it.copy(volume = volume.coerceIn(0.05f, 1f)) }
    }

    fun setFadeIn(enabled: Boolean) {
        _draft.update { it.copy(fadeIn = enabled) }
    }

    fun setFadeSeconds(seconds: Int) {
        _draft.update { it.copy(fadeSeconds = seconds.coerceIn(1, Alarm.MAX_FADE_SECONDS)) }
    }

    // ---------------------------------------------------------------- recording

    fun startRecording(): Boolean {
        if (recorder.state.value is VoiceRecorder.RecordState.Recording) {
            _message.value = "recording-active"
            return false
        }
        player.stopPreview()
        val target = recordings.newFile()
        val result = recorder.start(target)
        return if (result.isSuccess) {
            true
        } else {
            _message.value = "recording-failed:" + (result.exceptionOrNull()?.message ?: "unknown")
            false
        }
    }

    fun stopRecording() {
        val previous = _draft.value.recordingFile
        val file = recorder.stop()
        if (file == null) {
            _message.value = "recording-too-short"
            return
        }
        if (previous != null && previous != originalRecording) {
            recordings.delete(previous)
        }
        _draft.update { it.copy(recordingFile = file.name) }
    }

    fun cancelRecording() {
        recorder.cancel()
    }

    fun deleteRecording() {
        val current = _draft.value.recordingFile
        player.stopPreview()
        _draft.update { it.copy(recordingFile = null) }
        if (current != null && current != originalRecording) {
            recordings.delete(current)
        }
    }

    fun toggleRecordingPreview() {
        val file = recordings.fileFor(_draft.value.recordingFile)
        if (file == null || !file.isFile) {
            _message.value = "recording-not-found"
            return
        }
        val current = player.preview.value
        if (current.file == file.name) {
            when {
                current.isPlaying -> player.pausePreview()
                current.positionMs in 1 until current.durationMs -> player.resumePreview()
                else -> player.startPreview(file)
            }
        } else {
            player.startPreview(file)
        }
    }

    fun stopPreviews() {
        player.stopPreview()
        tts.stop()
        _previewingSpeech.value = false
    }

    fun toggleSpeechPreview() {
        if (_previewingSpeech.value) {
            tts.stop()
            _previewingSpeech.value = false
            return
        }
        val text = _draft.value.text.trim()
        if (text.isEmpty()) {
            _message.value = "speech-empty"
            return
        }
        player.stopPreview()
        _previewingSpeech.value = true
        val snapshot = _draft.value
        viewModelScope.launch {
            // The engine may have been installed or switched while this screen was in the
            // background; rebuild it once before giving up.
            if (tts.status.value == TtsEngine.Status.Unavailable) tts.retry()
            val result = tts.speak(
                text = text,
                rate = snapshot.speechRate,
                preset = snapshot.voicePreset,
                customName = snapshot.voiceName,
                volume = 1f,
            )
            _previewingSpeech.value = false
            if (result.isFailure) {
                _message.value = if (tts.status.value == TtsEngine.Status.Ready) {
                    "tts-failed"
                } else {
                    "tts-unavailable"
                }
            }
        }
    }

    /** Rebuilds the speech engine after the user comes back from the system TTS settings. */
    fun retryTtsIfUnavailable() {
        if (tts.status.value == TtsEngine.Status.Unavailable) tts.retry()
    }

    // ---------------------------------------------------------------- persistence

    fun save(): Boolean {
        val alarm = _draft.value.toAlarm()
        if (!alarm.hasPlayableContent) {
            _message.value = "content-required"
            return false
        }
        val savedAlarm = repository.save(alarm)
        if (!scheduler.schedule(savedAlarm)) {
            _message.value = "schedule-failed"
            return false
        }
        if (originalRecording != null && originalRecording != savedAlarm.recordingFile) {
            recordings.delete(originalRecording)
        }
        recordings.cleanupOrphans(repository.alarms.value)
        _saved.value = true
        return true
    }

    fun delete(): Boolean {
        val alarm = original ?: return false
        if (repository.delete(alarm.id) == null) {
            _message.value = "delete-failed"
            return false
        }
        scheduler.cancel(alarm.id)
        recordings.delete(alarm.recordingFile)
        recordings.cleanupOrphans(repository.alarms.value)
        _saved.value = true
        return true
    }

    fun notifyMicPermissionDenied() {
        _message.value = "mic-permission"
    }

    fun consumeMessage() {
        _message.value = null
    }

    override fun onCleared() {
        recorder.cancel()
        player.stopPreview()
        tts.stop()
        if (!_saved.value) {
            val draftFile = _draft.value.recordingFile
            if (draftFile != null && draftFile != originalRecording &&
                repository.alarms.value.none { it.recordingFile == draftFile }
            ) {
                recordings.delete(draftFile)
            }
        }
    }

    private companion object {
        const val MAX_TEXT_LENGTH = 2000
    }
}
