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
import com.vocalarm.app.data.AlarmOccurrenceKind
import com.vocalarm.app.domain.Alarm
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
            AlarmDelivery.ACTION_DELIVER -> {
                container.triggerLog.recordDelivered()
                val delivery = AlarmDelivery.from(intent)
                val accepted = delivery != null && container.scheduler.consume(
                    delivery.alarmId, delivery.kind, delivery.token,
                )
                if (accepted) {
                    begin(delivery.alarmId, delivery.kind != AlarmOccurrenceKind.Regular)
                } else if (activeAlarm == null) {
                    // Even a stale system delivery starts an FGS; meet its five-second contract.
                    startForegroundCompat(buildPreparingNotification())
                    stopRinging()
                }
            }

            ACTION_STOP -> {
                if (activeAlarm == null) startForegroundCompat(buildPreparingNotification())
                stopRinging()
            }

            ACTION_SNOOZE -> {
                if (activeAlarm == null) startForegroundCompat(buildPreparingNotification())
                snooze(
                    minutes = intent.getIntExtra(EXTRA_MINUTES, 5),
                    requestedAlarmId = intent.getStringExtra(EXTRA_ALARM_ID),
                )
            }

            ACTION_SCREEN_SHOWN -> {
                val alarm = activeAlarm
                if (alarm == null) {
                    stopSelf()
                } else if (!ringScreenShown) {
                    ringScreenShown = true
                    fullScreenVisible = false
                    getSystemService(android.app.NotificationManager::class.java)
                        ?.notify(NOTIFICATION_ID, buildRingingNotification(alarm, showFullScreen = false))
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
        // Two alarms may arrive together. Stop the previous playback before replacing its session.
        ringJob?.cancel()
        container.playback.stop()
        container.tts.stop()
        vibrator?.cancel()
        releaseWakeLock()
        activeAlarm = alarm
        ringScreenShown = false
        container.ringing.start(alarm, isSnooze)
        container.triggerLog.recordRinging()

        // Consumption happens here, after Android has actually delivered the foreground service.

        // Post the full-screen intent before touching the display. Android only expands one while
        // the screen is off or the keyguard is up; lighting the screen first would make the system
        // treat this as an in-use device and demote our own alarm to a heads-up banner.
        fullScreenVisible = true
        startForegroundCompat(buildRingingNotification(alarm))
        if (!fullScreenIntentUsable()) {
            // Notifications blocked or the full-screen permission revoked: the system will never
            // expand the intent, so waking the display ourselves is the only thing left to do.
            wakeScreen()
        }
        AlarmPresentation.open(this, alarm.id)
        acquireWakeLock()
        ringJob = scope.launch { playUntilFinished(alarm) }
        watchRingScreenLaunch(alarm.id)
    }

    /**
     * Safety net for ROMs that drop the full-screen intent silently: when the ringing screen still
     * has not reported itself after a short grace period and the display is dark, wake it so the
     * user at least hears and sees the alarm.
     */
    private fun watchRingScreenLaunch(alarmId: String) {
        scope.launch {
            delay(RING_SCREEN_GRACE_MS)
            if (ringScreenShown || activeAlarm?.id != alarmId) return@launch
            if (AlarmPresentation.canOpenFromBackground(this@AlarmService)) {
                AlarmPresentation.open(this@AlarmService, alarmId)
            }
            val powerManager = getSystemService(PowerManager::class.java) ?: return@launch
            if (!powerManager.isInteractive) wakeScreen()
        }
    }

    /** Full-screen intents need both notifications and the Android 14+ special access grant. */
    private fun fullScreenIntentUsable(): Boolean =
        NotificationChannels.canAlertForAlarm(this) &&
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
        if (!container.scheduler.scheduleSnooze(alarmId, minutes)) {
            val alarm = activeAlarm
            if (alarm == null) {
                stopRinging()
            } else {
                val message = getString(R.string.ring_snooze_failed)
                container.ringing.setMessage(message)
                updateNotification(alarm, message)
            }
            return
        }
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
        fullScreenVisible = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        manager.notify(NOTIFICATION_ID, buildRingingNotification(alarm, message, fullScreenVisible))
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

    private fun buildRingingNotification(
        alarm: Alarm,
        message: String? = null,
        showFullScreen: Boolean = true,
    ): Notification {
        val fullScreen = AlarmPresentation.pendingIntent(this, alarm.id)
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
            .setOnlyAlertOnce(true)
            .setContentIntent(fullScreen)
        if (showFullScreen) builder.setFullScreenIntent(fullScreen, true)
        notificationActions(alarm, actionIcon).forEach(builder::addAction)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
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
        const val ACTION_STOP = "com.vocalarm.app.action.STOP_RINGING"
        const val ACTION_SNOOZE = "com.vocalarm.app.action.SNOOZE_RINGING"
        const val ACTION_SCREEN_SHOWN = "com.vocalarm.app.action.RING_SCREEN_SHOWN"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_MINUTES = "extra_minutes"

        private const val NOTIFICATION_ID = 1001
        private const val SNOOZE_NOTIFICATION_ID = 1002
        private const val REQUEST_STOP = 12
        private const val REQUEST_SNOOZE_BASE = 20
        private const val WAKE_LOCK_TAG = "Vocalarm:Ringing"
        private const val SCREEN_WAKE_LOCK_TAG = "Vocalarm:RingScreen"
        private const val SCREEN_WAKE_LOCK_TIMEOUT_MS = 5 * 60 * 1000L
        private const val RING_SCREEN_GRACE_MS = 3_000L
        private const val MAX_RING_MS = 30 * 60 * 1000L
        private const val FAILED_RING_MS = 60 * 1000L

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
