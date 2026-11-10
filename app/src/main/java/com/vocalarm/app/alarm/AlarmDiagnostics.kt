package com.vocalarm.app.alarm

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.vocalarm.app.AppContainer
import com.vocalarm.app.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local, user-copyable evidence. Deliberately excludes alarm labels, recordings and speech text. */
object AlarmDiagnostics {
    fun report(context: Context, container: AppContainer): String = buildString {
        val snapshot = container.triggerLog.read()
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        appendLine("Vocalarm ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("android=${Build.VERSION.RELEASE}; sdk=${Build.VERSION.SDK_INT}; build=${Build.DISPLAY}")
        appendLine("capturedAt=${stamp(System.currentTimeMillis())}")
        appendLine("exactAlarms=${container.scheduler.canScheduleExact()}")
        appendLine("notifications=${NotificationChannels.areNotificationsEnabled(context)}")
        appendLine("alarmChannel=${NotificationChannels.canAlertForAlarm(context)}")
        appendLine("fullScreen=${NotificationChannels.canUseFullScreenIntent(context)}")
        appendLine("backgroundScreen=${AlarmPresentation.canOpenFromBackground(context)}")
        appendLine("batteryExempt=${powerManager?.isIgnoringBatteryOptimizations(context.packageName)}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            appendLine("androidBackgroundRestricted=${activityManager?.isBackgroundRestricted}")
        }
        appendLine("vendorAutoStart=unknown; vendorBackgroundActivity=unknown")
        appendLine("guardianEnabled=${container.settingsRepository.settings.value.guardianEnabled}; running=${GuardianService.running}")
        appendLine("nextSavedAt=${stamp(container.scheduler.nextScheduledAt() ?: 0L)}")
        appendLine("lastScheduledAt=${stamp(snapshot.lastScheduledAt)}")
        appendLine("lastDeliveredAt=${stamp(snapshot.lastDeliveredAt)}; source=${snapshot.lastDeliverySource}; count=${snapshot.deliveredCount}")
        if (snapshot.lastScheduledAt > 0L) {
            appendLine("deliveryDelayMs=${snapshot.lastDeliveredAt - snapshot.lastScheduledAt}")
        }
        appendLine("lastRingingAt=${stamp(snapshot.lastRingingAt)}; count=${snapshot.ringingCount}")
        appendLine("lastScreenShownAt=${stamp(snapshot.lastScreenShownAt)}")
        appendLine("lastAppOpenedAt=${stamp(snapshot.lastAppOpenedAt)}")
        appendLine("lastTaskRemovedCallbackAt=${stamp(snapshot.lastTaskRemovedAt)}")
        appendLine("lastOverdueRecoveryAt=${stamp(snapshot.lastRecoveryAt)}; source=${snapshot.lastRecoverySource}")
        appendLine("recoveredScheduledAt=${stamp(snapshot.lastRecoveredScheduledAt)}")
        appendLine("lastFailureAt=${stamp(snapshot.lastFailureAt)}; ${snapshot.lastFailure}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            runCatching { activityManager?.getHistoricalProcessStartReasons(5).orEmpty() }
                .onSuccess { starts ->
                    appendLine("processStarts (wasForceStopped reports the OS stopped state, not who caused it):")
                    starts.forEach { start ->
                        appendLine("pid=${start.pid}; reason=${start.reason}; type=${start.startType}; wasForceStopped=${start.wasForceStopped()}; timestampsNs=${start.startupTimestamps}")
                    }
                }
                .onFailure { appendLine("processStartsUnavailable=${it.javaClass.simpleName}") }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { activityManager?.getHistoricalProcessExitReasons(context.packageName, 0, 5).orEmpty() }
                .onSuccess { exits ->
                    appendLine("processExits (reason codes are Android's records, not a vendor-policy diagnosis):")
                    exits.forEach { exit ->
                        appendLine("${stamp(exit.timestamp)}; reason=${exit.reason}; status=${exit.status}; importance=${exit.importance}; ${exit.description.orEmpty().replace('\n', ' ').take(300)}")
                    }
                }
                .onFailure { appendLine("processExitsUnavailable=${it.javaClass.simpleName}") }
        }
    }

    private fun stamp(millis: Long): String = if (millis <= 0) "none" else
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT).format(Date(millis))
}
