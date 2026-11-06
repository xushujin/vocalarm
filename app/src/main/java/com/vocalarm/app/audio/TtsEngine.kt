package com.vocalarm.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.vocalarm.app.domain.TtsTextSplitter
import com.vocalarm.app.domain.VoiceCatalog
import com.vocalarm.app.domain.VoiceDescriptor
import com.vocalarm.app.domain.VoicePreset
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the single TextToSpeech instance used by both preview and ringing. Speaking is exposed as a
 * suspend function so the playback engine can sequence "recording, then speech" deterministically.
 */
@Suppress("OVERRIDE_DEPRECATION")
class TtsEngine(context: Context) {

    enum class Status { Initializing, Ready, Unavailable }

    /** Why [Status.Unavailable] was reported, so the UI can point at the right fix. */
    enum class Failure { NoEngine, InitFailed }

    private val pending = ConcurrentHashMap<String, CancellableContinuation<Result<Unit>>>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _status = MutableStateFlow(Status.Initializing)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _voices = MutableStateFlow<List<VoiceDescriptor>>(emptyList())
    val voices: StateFlow<List<VoiceDescriptor>> = _voices.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _failure = MutableStateFlow<Failure?>(null)
    val failure: StateFlow<Failure?> = _failure.asStateFlow()

    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            complete(utteranceId, Result.success(Unit))
        }

        override fun onError(utteranceId: String?) {
            complete(utteranceId, Result.failure(IllegalStateException("tts-error")))
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            complete(utteranceId, Result.failure(IllegalStateException("tts-error-$errorCode")))
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            complete(utteranceId, Result.failure(CancellationException("tts-stopped")))
        }
    }

    init {
        createEngine()
    }

    private fun createEngine() {
        val created = runCatching {
            TextToSpeech(appContext) { initStatus ->
                if (textToSpeech == null) {
                    // The engine called back before the constructor returned; wait for assignment.
                    mainHandler.post { onInitialized(initStatus) }
                } else {
                    onInitialized(initStatus)
                }
            }
        }.getOrNull()
        textToSpeech = created
        if (created == null) {
            markUnavailable("tts-init-exception")
        }
    }

    /**
     * Rebuilds the engine. Needed after the user installs, enables or switches a TTS engine in the
     * system settings: the instance created at process start stays dead otherwise.
     */
    fun retry() {
        if (_status.value != Status.Unavailable) return
        textToSpeech?.let { engine -> runCatching { engine.shutdown() } }
        textToSpeech = null
        _failure.value = null
        _lastError.value = null
        _status.value = Status.Initializing
        createEngine()
    }

    private fun markUnavailable(error: String) {
        _status.value = Status.Unavailable
        _lastError.value = error
        _failure.value = if (hasInstalledEngine()) Failure.InitFailed else Failure.NoEngine
    }

    /** Package visibility for TTS is granted by the `<queries>` entry in the manifest. */
    private fun hasInstalledEngine(): Boolean = runCatching {
        appContext.packageManager
            .queryIntentServices(
                android.content.Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
                0,
            )
            .isNotEmpty()
    }.getOrDefault(false)

    private fun onInitialized(initStatus: Int) {
        val engine = textToSpeech ?: return
        if (initStatus != TextToSpeech.SUCCESS) {
            markUnavailable("tts-init-$initStatus")
            return
        }
        _failure.value = null
        runCatching {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            engine.setOnUtteranceProgressListener(progressListener)
        }
        val language = runCatching { engine.setLanguage(Locale.SIMPLIFIED_CHINESE) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        val fallback = if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            runCatching { engine.setLanguage(Locale.getDefault()) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        } else {
            language
        }
        _status.value = Status.Ready
        if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
            _lastError.value = "tts-language-unavailable"
        }
        _voices.value = readVoices(engine)
    }

    private fun readVoices(engine: TextToSpeech): List<VoiceDescriptor> {
        val available = runCatching { engine.voices }.getOrNull() ?: return emptyList()
        return available.mapNotNull { voice ->
            runCatching {
                VoiceDescriptor(
                    name = voice.name,
                    localeTag = voice.locale.toLanguageTag(),
                    features = voice.features.orEmpty(),
                )
            }.getOrNull()
        }.let(VoiceCatalog::sortedForDisplay)
    }

    fun resolveVoice(preset: VoicePreset, customName: String?): VoiceDescriptor? =
        VoiceCatalog.resolve(preset, customName, _voices.value)

    fun resolvedVoiceDisplayName(preset: VoicePreset, customName: String?): String? =
        resolveVoice(preset, customName)?.displayName

    /**
     * Speaks [text] chunk by chunk. Returns failure when TTS is unavailable or the engine reports an
     * error; the caller decides whether the alarm should continue with the next content.
     */
    suspend fun speak(
        text: String,
        rate: Float,
        preset: VoicePreset,
        customName: String?,
        volume: Float,
    ): Result<Unit> {
        if (text.isBlank()) return Result.failure(IllegalArgumentException("empty text"))
        val engine = textToSpeech
        if (engine == null || !awaitReady()) {
            return Result.failure(IllegalStateException("tts-unavailable"))
        }

        stop()
        applyVoice(engine, preset, customName)
        runCatching { engine.setSpeechRate(rate.coerceIn(0.5f, 2.0f)) }

        val chunks = TtsTextSplitter.split(text)
        if (chunks.isEmpty()) return Result.failure(IllegalArgumentException("empty text"))
        for ((index, chunk) in chunks.withIndex()) {
            val result = speakChunk(
                engine = engine,
                text = chunk,
                volume = volume,
                queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
            )
            if (result.isFailure) return result
        }
        return Result.success(Unit)
    }

    private fun applyVoice(engine: TextToSpeech, preset: VoicePreset, customName: String?) {
        val chosen = resolveVoice(preset, customName) ?: return
        val engineVoice = runCatching { engine.voices }
            .getOrNull()
            ?.firstOrNull { it.name == chosen.name }
            ?: return
        runCatching { engine.voice = engineVoice }
    }

    private suspend fun speakChunk(
        engine: TextToSpeech,
        text: String,
        volume: Float,
        queueMode: Int,
    ): Result<Unit> = suspendCancellableCoroutine<Result<Unit>> { continuation ->
        val utteranceId = UUID.randomUUID().toString()
        pending[utteranceId] = continuation
        val params = Bundle().apply {
            // TextToSpeech.Engine.KEY_PARAM_VOLUME is deprecated; the literal key is stable.
            putFloat("volume", volume.coerceIn(0f, 1f))
        }
        val code = runCatching {
            engine.speak(text, queueMode, params, utteranceId)
        }.getOrElse { TextToSpeech.ERROR }
        if (code != TextToSpeech.SUCCESS) {
            pending.remove(utteranceId)
            if (continuation.isActive) {
                continuation.resume(Result.failure(IllegalStateException("tts-speak-$code")))
            }
        }
        continuation.invokeOnCancellation {
            pending.remove(utteranceId)
            runCatching { engine.stop() }
        }
    }

    private fun complete(utteranceId: String?, result: Result<Unit>) {
        val continuation = utteranceId?.let { pending.remove(it) } ?: return
        if (continuation.isActive) continuation.resume(result)
    }

    private suspend fun awaitReady(): Boolean {
        if (_status.value == Status.Ready) return true
        if (_status.value == Status.Unavailable) return false
        withTimeoutOrNull(TTS_READY_TIMEOUT_MS) {
            _status.first { it != Status.Initializing }
        }
        return _status.value == Status.Ready
    }

    fun stop() {
        textToSpeech?.let { engine -> runCatching { engine.stop() } }
        val snapshot = pending.keys.toList()
        snapshot.forEach { id ->
            val continuation = pending.remove(id) ?: return@forEach
            if (continuation.isActive) {
                continuation.resume(Result.failure(CancellationException("tts-stopped")))
            }
        }
    }

    fun shutdown() {
        stop()
        textToSpeech?.let { engine -> runCatching { engine.shutdown() } }
        textToSpeech = null
        _status.value = Status.Unavailable
    }

    companion object {
        private const val TTS_READY_TIMEOUT_MS = 8_000L
    }
}
