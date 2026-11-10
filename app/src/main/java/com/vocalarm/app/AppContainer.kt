package com.vocalarm.app

import android.content.Context
import com.vocalarm.app.alarm.AlarmPlaybackEngine
import com.vocalarm.app.alarm.AlarmScheduler
import com.vocalarm.app.alarm.RingingState
import com.vocalarm.app.alarm.TriggerLog
import com.vocalarm.app.audio.AudioPlayer
import com.vocalarm.app.audio.TtsEngine
import com.vocalarm.app.audio.VoiceRecorder
import com.vocalarm.app.data.AlarmRepository
import com.vocalarm.app.data.JsonStore
import com.vocalarm.app.data.RecordingStore
import com.vocalarm.app.data.SettingsRepository
import com.vocalarm.app.update.UpdateApi
import com.vocalarm.app.update.UpdateRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container. No DI framework: one instance per process, created by the
 * Application and passed down through composables.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settingsRepository = SettingsRepository(
        store = JsonStore(File(appContext.filesDir, "settings.json")),
        scope = appScope,
    )

    val alarmRepository = AlarmRepository(
        store = JsonStore(File(appContext.filesDir, "alarms.json")),
        scope = appScope,
    )

    val recordings = RecordingStore(File(appContext.filesDir, "recordings"))

    val player = AudioPlayer(scope = appScope)

    val tts = TtsEngine(appContext)

    val recorder = VoiceRecorder(context = appContext, scope = appScope)

    val playback = AlarmPlaybackEngine(player, tts)

    val triggerLog = TriggerLog(File(appContext.filesDir, "trigger.log"))

    val scheduler = AlarmScheduler(appContext, alarmRepository, triggerLog)

    val ringing = RingingState()

    val updateRepository = UpdateRepository(
        context = appContext,
        scope = appScope,
        api = UpdateApi(BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO),
    )

    fun cleanupOrphanRecordings(): List<String> =
        recordings.cleanupOrphans(alarmRepository.alarms.value)
}
