package com.vocalarm.app.domain

import java.time.DayOfWeek
import java.util.Locale

enum class LoopMode { Once, Times, Continuous }

enum class VoicePreset { Default, Female, Male, Custom }

/**
 * Immutable alarm definition. Instances are shared between the repository, the scheduler and the
 * ringing service, so every field has to be meaningful to all three.
 */
data class Alarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val enabled: Boolean = true,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val recordingFile: String? = null,
    val text: String? = null,
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
    val updatedAt: Long = 0L,
) {

    val timeText: String
        get() = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    val hasRecording: Boolean get() = !recordingFile.isNullOrBlank()

    val hasText: Boolean get() = !text.isNullOrBlank()

    val hasPlayableContent: Boolean get() = hasRecording || hasText

    fun normalized(): Alarm = copy(
        hour = hour.coerceIn(0, 23),
        minute = minute.coerceIn(0, 59),
        label = label.trim().take(60),
        repeatDays = repeatDays.filter { it.value in 1..7 }.toSet(),
        text = text?.trim()?.takeIf { it.isNotEmpty() },
        recordingFile = recordingFile?.takeIf { it.isNotBlank() },
        speechRate = speechRate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE),
        loopCount = loopCount.coerceIn(1, MAX_LOOP_COUNT),
        volume = volume.coerceIn(0.05f, 1.0f),
        fadeSeconds = fadeSeconds.coerceIn(0, MAX_FADE_SECONDS),
        snoozeMinutes = snoozeMinutes.filter { it in 1..60 }.distinct().sorted().ifEmpty { listOf(5, 10) },
    )

    companion object {
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2.0f
        const val MAX_LOOP_COUNT = 20
        const val MAX_FADE_SECONDS = 60
    }
}
