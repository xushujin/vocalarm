package com.vocalarm.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings

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

    fun notificationSettingsAction(context: Context): android.content.Intent =
        android.content.Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
}
