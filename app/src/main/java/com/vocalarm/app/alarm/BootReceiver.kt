package com.vocalarm.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vocalarm.app.VocalarmApp

/**
 * Alarms are cleared by the system on reboot, after an app update and when the clock is changed.
 * This receiver re-arms every enabled alarm without touching stored data.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return
        val app = context.applicationContext as? VocalarmApp ?: return
        app.container.scheduler.rescheduleAll(
            source = AlarmScheduler.RestoreSource.BootOrClockChange,
            recalculate = action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED,
        )
        AlarmWatchdog.schedule(context)
        if (app.container.settingsRepository.settings.value.guardianEnabled) {
            GuardianService.start(context)
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
