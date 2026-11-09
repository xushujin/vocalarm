package com.vocalarm.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.net.toUri

object NotificationChannels {

    const val ALARM = "alarm_ringing"
    const val ALARM_STATUS = "alarm_status"
    const val ALARM_ONGOING = "alarm_ongoing"
    const val UPDATE = "app_update"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(ALARM) == null) {
            val channel = NotificationChannel(
                ALARM,
                context.getString(com.vocalarm.app.R.string.channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(com.vocalarm.app.R.string.channel_alarm_description)
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 300, 400)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(false)
            }
            manager.createNotificationChannel(channel)
        }
        if (manager.getNotificationChannel(ALARM_ONGOING) == null) {
            val channel = NotificationChannel(
                ALARM_ONGOING,
                context.getString(com.vocalarm.app.R.string.channel_ongoing_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(com.vocalarm.app.R.string.channel_ongoing_description)
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
        if (manager.getNotificationChannel(ALARM_STATUS) == null) {
            val channel = NotificationChannel(
                ALARM_STATUS,
                context.getString(com.vocalarm.app.R.string.channel_status_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(com.vocalarm.app.R.string.channel_status_description)
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
        if (manager.getNotificationChannel(UPDATE) == null) {
            val channel = NotificationChannel(
                UPDATE,
                context.getString(com.vocalarm.app.R.string.channel_update_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(com.vocalarm.app.R.string.channel_update_description)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.areNotificationsEnabled()
    }

    fun canAlertForAlarm(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val channel = manager.getNotificationChannel(ALARM) ?: return false
        return manager.areNotificationsEnabled() && channel.importance >= NotificationManager.IMPORTANCE_HIGH
    }

    fun alarmChannelSettingsAction(context: Context): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, ALARM)

    fun notificationSettingsAction(context: Context): android.content.Intent =
        android.content.Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Starting with Android 14 the full-screen intent is a user-toggleable permission: when it is
     * missing the system silently downgrades the alarm to a heads-up notification and the ringing
     * screen never covers the keyguard. Older platforms always allow it.
     */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching { manager.canUseFullScreenIntent() }.getOrDefault(false)
    }

    /** Opens the special app access page where full-screen intents are granted or revoked. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun fullScreenIntentSettingsAction(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
