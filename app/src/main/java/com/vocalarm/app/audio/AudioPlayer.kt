package com.vocalarm.app.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class MediaPlaybackException(what: Int, extra: Int) :
    Exception("MediaPlayer error what=$what extra=$extra")

data class PreviewState(
    val file: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
) {
    val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * Single MediaPlayer wrapper for both preview playback (pause/resume) and alarm playback
 * (suspend until finished with a live gain value).
 */
class AudioPlayer(
    private val scope: CoroutineScope,
) {

    private var previewPlayer: MediaPlayer? = null
    private var previewJob: Job? = null

    private val _preview = MutableStateFlow(PreviewState())
    val preview: StateFlow<PreviewState> = _preview.asStateFlow()

    private val _previewError = MutableStateFlow<String?>(null)
    val previewError: StateFlow<String?> = _previewError.asStateFlow()

    fun startPreview(file: File, volume: Float = 1f) {
        stopPreview()
        _previewError.value = null
        if (!file.isFile) {
            _previewError.value = "missing-file"
            return
        }
        val player = MediaPlayer()
        val prepared = runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            player.setDataSource(file.absolutePath)
            player.prepare()
        }
        if (prepared.isFailure) {
            runCatching { player.release() }
            _previewError.value = prepared.exceptionOrNull()?.message ?: "prepare-failed"
            return
        }
        player.setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f))
        player.setOnCompletionListener {
            _preview.update { state ->
                state.copy(isPlaying = false, positionMs = state.durationMs)
            }
            previewJob?.cancel()
        }
        player.setOnErrorListener { _, what, extra ->
            _previewError.value = "MediaPlayer $what/$extra"
            _preview.update { it.copy(isPlaying = false) }
            true
        }
        runCatching { player.start() }.onFailure {
            _previewError.value = it.message ?: "start-failed"
            runCatching { player.release() }
            return
        }
        previewPlayer = player
        _preview.value = PreviewState(
            file = file.name,
            isPlaying = true,
            positionMs = 0L,
            durationMs = player.duration.toLong().coerceAtLeast(0L),
        )
        previewJob = scope.launch {
            while (isActive) {
                val playerRef = previewPlayer ?: break
                val position = runCatching { playerRef.currentPosition.toLong() }.getOrDefault(0L)
                _preview.update { it.copy(positionMs = position) }
                delay(200)
            }
        }
    }

    fun pausePreview() {
        val player = previewPlayer ?: return
        runCatching {
            if (player.isPlaying) {
                player.pause()
                _preview.update { it.copy(isPlaying = false) }
            }
        }
    }

    fun resumePreview() {
        val player = previewPlayer ?: return
        runCatching {
            if (!player.isPlaying) {
                player.start()
                _preview.update { it.copy(isPlaying = true) }
                if (previewJob?.isActive != true) {
                    previewJob = scope.launch {
                        while (isActive) {
                            val ref = previewPlayer ?: break
                            val position = runCatching { ref.currentPosition.toLong() }.getOrDefault(0L)
                            _preview.update { it.copy(positionMs = position) }
                            delay(200)
                        }
                    }
                }
            }
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        val player = previewPlayer
        previewPlayer = null
        if (player != null) {
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.release() }
        }
        _preview.value = PreviewState()
    }

    /**
     * Plays [file] once and suspends until completion. [gain] is polled so the service can fade in
     * without restarting playback when the volume changes.
     */
    suspend fun playOnceAwait(file: File, gain: () -> Float): Result<Unit> {
        stopPreview()
        if (!file.isFile) return Result.failure(IllegalStateException("missing recording"))
        return coroutineScopePlay(file, gain)
    }

    private suspend fun coroutineScopePlay(file: File, gain: () -> Float): Result<Unit> {
        var result: Result<Unit> = Result.failure(IllegalStateException("not started"))
        kotlinx.coroutines.coroutineScope {
            val player = MediaPlayer()
            val prepared = runCatching {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                player.setDataSource(file.absolutePath)
                player.prepare()
            }
            if (prepared.isFailure) {
                runCatching { player.release() }
                result = Result.failure(prepared.exceptionOrNull() ?: IllegalStateException("prepare failed"))
                return@coroutineScope
            }
            player.setVolume(0f, 0f)
            val ticker = launch(Dispatchers.Main.immediate) {
                while (isActive) {
                    val value = gain().coerceIn(0f, 1f)
                    runCatching { player.setVolume(value, value) }
                    delay(80)
                }
            }
            try {
                result = withContext(Dispatchers.Main.immediate) {
                    suspendCancellableCoroutine { continuation ->
                        player.setOnCompletionListener {
                            if (continuation.isActive) continuation.resume(Result.success(Unit))
                        }
                        player.setOnErrorListener { _, what, extra ->
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(MediaPlaybackException(what, extra)))
                            }
                            true
                        }
                        continuation.invokeOnCancellation { runCatching { player.stop() } }
                        val started = runCatching { player.start() }
                        if (started.isFailure && continuation.isActive) {
                            continuation.resume(
                                Result.failure(
                                    started.exceptionOrNull() ?: IllegalStateException("start failed"),
                                ),
                            )
                        }
                    }
                }
            } finally {
                ticker.cancel()
                runCatching { player.release() }
            }
        }
        return result
    }

    fun release() {
        stopPreview()
    }
}
