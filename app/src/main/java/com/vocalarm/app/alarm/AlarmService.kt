package com.vocalarm.app.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import com.vocalarm.app.AppContainer
import com.vocalarm.app.R
import com.vocalarm.app.VocalarmApp
import com.vocalarm.app.domain.Alarm
import com.vocalarm.app.ui.ring.AlarmRingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service that owns a ringing session. It keeps the CPU awake, posts the full-screen
 * alarm notification, plays the content, and tears everything down on dismiss or snooze.
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: AppContainer
    private var ringJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var activeAlarm: Alarm? = null
    private var fullScreenVisible = false
    private var ringScreenShown = false

    override fun onCreate() {
        super.onCreate()
        container = (application as VocalarmApp).container
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> {
                val alarmId = intent.getStringExtra(EXTRA_ALARM_ID)
                if (alarmId.isNullOrBlank()) {
                    startForegroundCompat(buildPreparingNotification())
                    stopRinging()
                } else {
                    // Never post the "preparing" placeholder on this path: a second notification
                    // right before the full-screen one lets the system mute the alarm as
                    // "recently noisy" and drop the full-screen launch with it.
                    begin(alarmId, intent.getBooleanExtra(EXTRA_SNOOZE, false))
                }
            }

            ACTION_STOP -> {
                startForegroundCompat(buildPreparingNotification())
                stopRinging()
            }

            ACTION_SNOOZE -> {
                startForegroundCompat(buildPreparingNotification())
                snooze(
                    minutes = intent.getIntExtra(EXTRA_MINUTES, 5),
                    requestedAlarmId = intent.getStringExtra(EXTRA_ALARM_ID),
                )
            }

            ACTION_SCREEN_SHOWN -> {
                val alarm = activeAlarm
                if (alarm == null) {
                    stopSelf()
                } else {
                    ringScreenShown = true
                    cancelFullScreenNotification()
                    // The ringing activity keeps the display on by itself from now on.
                    releaseScreenWakeLock()
                }
            }

            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ringJob?.cancel()
        container.playback.stop()
        container.tts.stop()
        container.ringing.stop()
        releaseWakeLock()
        vibrator?.cancel()
        vibrator = null
        scope.cancel()
        super.onDestroy()
    }

    private fun begin(alarmId: String, isSnooze: Boolean) {
        val alarm = container.alarmRepository.find(alarmId) ?: run {
            // Started as a foreground service, so satisfy the notification requirement first.
            startForegroundCompat(buildPreparingNotification())
            stopRinging()
            return
        }
        activeAlarm = alarm
        ringScreenShown = false
        container.ringing.start(alarm, isSnooze)
        container.triggerLog.recordRinging()

        // Keep the series alive before audio starts: a crash during playback must not skip tomorrow.
        if (!isSnooze) {
            if (alarm.repeatDays.isEmpty()) {
                container.alarmRepository.setEnabled(alarmId, false)
                // A cold start re-armed this alarm for tomorrow before the broadcast arrived, so a
                // one-shot alarm has to be cancelled explicitly or it rings again the next day.
                container.scheduler.cancel(alarmId)
            } else {
                container.scheduler.schedule(alarm)
            }
        }

        // Post the full-screen intent before touching the display. Android only expands one while
        // the screen is off or the keyguard is up; lighting the screen first would make the system
        // treat this as an in-use device and demote our own alarm to a heads-up banner.
        postFullScreenNotification(alarm)
        if (!fullScreenIntentUsable()) {
            // Notifications blocked or the full-screen permission revoked: the system will never
            // expand the intent, so waking the display ourselves is the only thing left to do.
            wakeScreen()
        }
        startForegroundCompat(buildOngoingNotification(alarm))
        launchRingScreen(alarm)
        acquireWakeLock()
        ringJob = scope.launch { playUntilFinished(alarm) }
        watchRingScreenLaunch()
    }

    /**
     * Safety net for ROMs that drop the full-screen intent silently: when the ringing screen still
     * has not reported itself after a short grace period and the display is dark, wake it so the
     * user at least hears and sees the alarm.
     */
    private fun watchRingScreenLaunch() {
        scope.launch {
            delay(RING_SCREEN_GRACE_MS)
            if (ringScreenShown) return@launch
            val powerManager = getSystemService(PowerManager::class.java) ?: return@launch
            if (!powerManager.isInteractive) wakeScreen()
        }
    }

    /** Full-screen intents need both notifications and the Android 14+ special access grant. */
    private fun fullScreenIntentUsable(): Boolean =
        NotificationChannels.areNotificationsEnabled(this) &&
            NotificationChannels.canUseFullScreenIntent(this)

    private suspend fun playUntilFinished(alarm: Alarm) {
        val recording = container.recordings.fileFor(alarm.recordingFile)
        val outcome = withTimeoutOrNull(MAX_RING_MS) {
            runCatching {
                container.playback.run(alarm, recording, SystemClock.elapsedRealtime())
            }.getOrElse { error -> Result.failure(error) }
        }
        if (outcome == null) {
            stopRinging()
            return
        }
        if (outcome.isFailure) {
            val message = getString(R.string.ring_audio_failed)
            container.ringing.setMessage(message)
            updateNotification(alarm, message)
            startVibrationFallback()
            delay(FAILED_RING_MS)
        }
        stopRinging()
    }

    private fun snooze(minutes: Int, requestedAlarmId: String? = null) {
        val alarmId = requestedAlarmId?.takeIf { it.isNotBlank() }
            ?: activeAlarm?.id
            ?: container.ringing.current.value?.alarm?.id
        if (alarmId.isNullOrBlank()) {
            stopRinging()
            return
        }
        container.scheduler.scheduleSnooze(alarmId, minutes)
        postSnoozedNotification(minutes)
        stopRinging()
    }

    private fun stopRinging() {
        ringJob?.cancel()
        ringJob = null
        container.playback.stop()
        container.tts.stop()
        container.ringing.stop()
        releaseWakeLock()
        vibrator?.cancel()
        vibrator = null
        activeAlarm = null
        cancelFullScreenNotification()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun launchRingScreen(alarm: Alarm) {
        val intent = AlarmRingActivity.createIntent(this, alarm.id).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // On Android 10+ this can be blocked while in the background; the full-screen intent in the
        // notification is the guaranteed path, this call only makes it instant when allowed.
        runCatching { startActivity(intent) }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(MAX_RING_MS + 60_000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock -> if (lock.isHeld) runCatching { lock.release() } }
        wakeLock = null
        releaseScreenWakeLock()
    }

    /**
     * Turns the display on and holds it lit. This is a fallback: it runs only when the system
     * cannot or will not expand the full-screen intent, and it costs us the full-screen expansion
     * for that session because a lit screen no longer counts as "in the user's pocket".
     */
    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        if (screenWakeLock != null) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        runCatching {
            screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                SCREEN_WAKE_LOCK_TAG,
            ).apply {
                setReferenceCounted(false)
                acquire(SCREEN_WAKE_LOCK_TIMEOUT_MS)
            }
        }
    }

    private fun releaseScreenWakeLock() {
        screenWakeLock?.let { lock -> if (lock.isHeld) runCatching { lock.release() } }
        screenWakeLock = null
    }

    private fun startVibrationFallback() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        } ?: return
        this.vibrator = vibrator
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 300, 700, 300, 1000), 0)
        runCatching { vibrator.vibrate(effect) }
    }

    private fun updateNotification(alarm: Alarm, message: String) {
        val manager = getSystemService(android.app.NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildOngoingNotification(alarm, message))
        if (fullScreenVisible) {
            manager.notify(FULLSCREEN_NOTIFICATION_ID, buildFullScreenNotification(alarm, message))
        }
    }

    private fun postFullScreenNotification(alarm: Alarm) {
        val manager = getSystemService(android.app.NotificationManager::class.java) ?: return
        manager.notify(FULLSCREEN_NOTIFICATION_ID, buildFullScreenNotification(alarm))
        fullScreenVisible = true
    }

    private fun cancelFullScreenNotification() {
        if (!fullScreenVisible) return
        getSystemService(android.app.NotificationManager::class.java)
            ?.cancel(FULLSCREEN_NOTIFICATION_ID)
        fullScreenVisible = false
    }

    private fun postSnoozedNotification(minutes: Int) {
        val notification = Notification.Builder(this, NotificationChannels.ALARM_STATUS)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(getString(R.string.notification_snoozed_title))
            .setContentText(getString(R.string.notification_snoozed_text, minutes))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        getSystemService(android.app.NotificationManager::class.java)
            ?.notify(SNOOZE_NOTIFICATION_ID, notification)
    }

    private fun buildPreparingNotification(): Notification =
        Notification.Builder(this, NotificationChannels.ALARM_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(getString(R.string.notification_preparing))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun buildFullScreenNotification(alarm: Alarm, message: String? = null): Notification {
        val fullScreen = PendingIntent.getActivity(
            this,
            REQUEST_RING_SCREEN,
            AlarmRingActivity.createIntent(this, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val actionIcon = Icon.createWithResource(this, R.drawable.ic_stat_alarm)
        val title = alarm.label.ifBlank { getString(R.string.notification_alarm_title) }
        val text = message ?: buildString {
            append(getString(R.string.notification_alarm_text, alarm.timeText))
            val parts = buildList {
                if (alarm.hasRecording) add(getString(R.string.content_recording))
                if (alarm.hasText) add(getString(R.string.content_speech))
            }
            if (parts.isNotEmpty()) {
                append(" · ")
                append(parts.joinToString(getString(R.string.content_separator)))
            }
        }
        val builder = Notification.Builder(this, NotificationChannels.ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
        notificationActions(alarm, actionIcon).forEach(builder::addAction)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun buildOngoingNotification(alarm: Alarm, message: String? = null): Notification {
        val content = PendingIntent.getActivity(
            this,
            REQUEST_RING_SCREEN,
            AlarmRingActivity.createIntent(this, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(this, R.drawable.ic_stat_alarm)
        val builder = Notification.Builder(this, NotificationChannels.ALARM_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(alarm.label.ifBlank { getString(R.string.notification_ongoing_title) })
            .setContentText(message ?: getString(R.string.notification_ongoing_text, alarm.timeText))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(content)
        notificationActions(alarm, icon).forEach(builder::addAction)
        return builder.build()
    }

    private fun notificationActions(alarm: Alarm, icon: Icon): List<Notification.Action> {
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val actions = mutableListOf(
            Notification.Action.Builder(icon, getString(R.string.action_stop), stopIntent).build(),
        )
        alarm.snoozeMinutes.forEachIndexed { index, minutes ->
            val snoozeIntent = PendingIntent.getService(
                this,
                REQUEST_SNOOZE_BASE + index,
                Intent(this, AlarmService::class.java)
                    .setAction(ACTION_SNOOZE)
                    .putExtra(EXTRA_MINUTES, minutes),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            actions += Notification.Action.Builder(
                icon,
                getString(R.string.action_snooze, minutes),
                snoozeIntent,
            ).build()
        }
        return actions
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ACTION_START = "com.vocalarm.app.action.START_RINGING"
        const val ACTION_STOP = "com.vocalarm.app.action.STOP_RINGING"
        const val ACTION_SNOOZE = "com.vocalarm.app.action.SNOOZE_RINGING"
        const val ACTION_SCREEN_SHOWN = "com.vocalarm.app.action.RING_SCREEN_SHOWN"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_SNOOZE = "extra_snooze"
        const val EXTRA_MINUTES = "extra_minutes"

        private const val NOTIFICATION_ID = 1001
        private const val SNOOZE_NOTIFICATION_ID = 1002
        private const val FULLSCREEN_NOTIFICATION_ID = 1004
        private const val REQUEST_RING_SCREEN = 11
        private const val REQUEST_STOP = 12
        private const val REQUEST_SNOOZE_BASE = 20
        private const val WAKE_LOCK_TAG = "Vocalarm:Ringing"
        private const val SCREEN_WAKE_LOCK_TAG = "Vocalarm:RingScreen"
        private const val SCREEN_WAKE_LOCK_TIMEOUT_MS = 5 * 60 * 1000L
        private const val RING_SCREEN_GRACE_MS = 3_000L
        private const val MAX_RING_MS = 30 * 60 * 1000L
        private const val FAILED_RING_MS = 60 * 1000L

        fun start(context: Context, alarmId: String, snooze: Boolean) {
            val intent = Intent(context, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_SNOOZE, snooze)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlarmService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }

        fun screenShown(context: Context) {
            val intent = Intent(context, AlarmService::class.java).setAction(ACTION_SCREEN_SHOWN)
            runCatching { context.startService(intent) }
        }

        fun snooze(context: Context, minutes: Int, alarmId: String) {
            val intent = Intent(context, AlarmService::class.java)
                .setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_MINUTES, minutes)
                .putExtra(EXTRA_ALARM_ID, alarmId)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
