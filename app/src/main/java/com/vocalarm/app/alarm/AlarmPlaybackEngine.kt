package com.vocalarm.app.alarm

import android.os.SystemClock
import com.vocalarm.app.audio.AudioPlayer
import com.vocalarm.app.audio.TtsEngine
import com.vocalarm.app.domain.Alarm
import com.vocalarm.app.domain.FadeGain
import com.vocalarm.app.domain.LoopPolicy
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive

/**
 * Plays one ringing session: recording first, then synthesized speech, repeated according to the
 * alarm's loop policy. Fade-in is computed from the session start so looping never resets it.
 */
class AlarmPlaybackEngine(
    private val player: AudioPlayer,
    private val tts: TtsEngine,
) {

    enum class Segment { Recording, Speech }

    data class NowPlaying(val segment: Segment, val round: Int)

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    suspend fun run(alarm: Alarm, recording: File?, sessionStart: Long): Result<Unit> {
        if (!alarm.hasPlayableContent) {
            return Result.failure(IllegalStateException("alarm has no content"))
        }
        val fadeMillis = if (alarm.fadeIn) FadeGain.fadeMillis(alarm.fadeSeconds) else 0L
        fun gain(): Float = FadeGain.gainAt(
            elapsedMillis = SystemClock.elapsedRealtime() - sessionStart,
            fadeMillis = fadeMillis,
            target = alarm.volume,
        )

        var playedSomething = false
        var lastError: Throwable? = null
        var completedRounds = 0

        while (currentCoroutineContext().isActive) {
            val round = completedRounds + 1
            if (recording != null) {
                if (recording.isFile) {
                    _nowPlaying.value = NowPlaying(Segment.Recording, round)
                    val result = player.playOnceAwait(recording, ::gain)
                    if (result.isSuccess) playedSomething = true else lastError = result.exceptionOrNull()
                } else {
                    lastError = IllegalStateException("recording missing")
                }
            }
            if (alarm.hasText) {
                _nowPlaying.value = NowPlaying(Segment.Speech, round)
                val result = tts.speak(
                    text = alarm.text.orEmpty(),
                    rate = alarm.speechRate,
                    preset = alarm.voicePreset,
                    customName = alarm.voiceName,
                    volume = gain(),
                )
                if (result.isSuccess) playedSomething = true else lastError = result.exceptionOrNull()
            }
            completedRounds += 1
            if (!LoopPolicy.shouldContinue(alarm.loopMode, alarm.loopCount, completedRounds)) break
            delay(ROUND_GAP_MS)
        }
        _nowPlaying.value = null
        return if (playedSomething) {
            Result.success(Unit)
        } else {
            Result.failure(lastError ?: IllegalStateException("playback produced no audio"))
        }
    }

    fun stop() {
        _nowPlaying.value = null
    }

    private companion object {
        const val ROUND_GAP_MS = 600L
    }
}

/** Shared between the service and the ringing screen so both observe the same session. */
class RingingState {

    data class Ringing(val alarm: Alarm, val isSnooze: Boolean, val message: String? = null)

    private val _current = MutableStateFlow<Ringing?>(null)
    val current: StateFlow<Ringing?> = _current.asStateFlow()

    fun start(alarm: Alarm, isSnooze: Boolean) {
        _current.value = Ringing(alarm, isSnooze)
    }

    /** Used when audio cannot be produced (for example a missing TTS engine). */
    fun setMessage(message: String?) {
        _current.value = _current.value?.copy(message = message)
    }

    fun stop() {
        _current.value = null
    }
}
