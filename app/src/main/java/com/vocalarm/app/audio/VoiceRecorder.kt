package com.vocalarm.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * MediaRecorder wrapper. Recording is always AAC in an MP4 container, mono 44.1 kHz, which every
 * Android device can play back without extra codecs.
 */
class VoiceRecorder(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    sealed interface RecordState {
        data object Idle : RecordState
        data class Recording(val elapsedMs: Long, val amplitude: Float) : RecordState
        data class Finished(val file: File, val durationMs: Long) : RecordState
        data class Failed(val reason: String) : RecordState
    }

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt = 0L
    private var meterJob: Job? = null

    private val _state = MutableStateFlow<RecordState>(RecordState.Idle)
    val state: StateFlow<RecordState> = _state.asStateFlow()

    fun start(target: File): Result<Unit> {
        if (recorder != null) return Result.failure(IllegalStateException("already recording"))
        val mediaRecorder = newRecorder()
        val prepared = runCatching {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioChannels(1)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setAudioEncodingBitRate(96_000)
            mediaRecorder.setOutputFile(target.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
        }
        if (prepared.isFailure) {
            runCatching { mediaRecorder.release() }
            runCatching { target.delete() }
            val reason = prepared.exceptionOrNull()?.message ?: "recorder-start-failed"
            _state.value = RecordState.Failed(reason)
            return Result.failure(prepared.exceptionOrNull() ?: IllegalStateException(reason))
        }
        recorder = mediaRecorder
        outputFile = target
        startedAt = SystemClock.elapsedRealtime()
        _state.value = RecordState.Recording(elapsedMs = 0L, amplitude = 0f)
        meterJob = scope.launch {
            while (isActive) {
                val ref = recorder ?: break
                val amplitude = runCatching { ref.maxAmplitude }.getOrDefault(0)
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                _state.value = RecordState.Recording(
                    elapsedMs = elapsed,
                    amplitude = normalizeAmplitude(amplitude),
                )
                delay(90)
            }
        }
        return Result.success(Unit)
    }

    fun stop(): File? {
        val active = recorder ?: return null
        meterJob?.cancel()
        meterJob = null
        val target = outputFile
        val duration = SystemClock.elapsedRealtime() - startedAt
        val stopped = runCatching { active.stop() }
        runCatching { active.release() }
        recorder = null
        outputFile = null
        if (stopped.isFailure || target == null) {
            target?.let { runCatching { it.delete() } }
            _state.value = RecordState.Failed(stopped.exceptionOrNull()?.message ?: "recorder-stop-failed")
            return null
        }
        if (duration < MIN_DURATION_MS || target.length() <= 0L) {
            runCatching { target.delete() }
            _state.value = RecordState.Failed("too-short")
            return null
        }
        _state.value = RecordState.Finished(target, duration)
        return target
    }

    fun cancel() {
        meterJob?.cancel()
        meterJob = null
        val active = recorder
        recorder = null
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        outputFile?.let { runCatching { it.delete() } }
        outputFile = null
        _state.value = RecordState.Idle
    }

    fun reset() {
        if (recorder == null) _state.value = RecordState.Idle
    }

    fun release() {
        cancel()
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

    private fun normalizeAmplitude(raw: Int): Float {
        if (raw <= 0) return 0f
        val ratio = (raw.toFloat() / 32_767f).coerceIn(0f, 1f)
        // Square root makes quiet speech visible without amplifying the noise floor too much.
        return min(1f, kotlin.math.sqrt(ratio))
    }

    companion object {
        const val MIN_DURATION_MS = 600L
    }
}
