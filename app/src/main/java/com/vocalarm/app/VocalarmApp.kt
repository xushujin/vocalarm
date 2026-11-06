package com.vocalarm.app

import android.app.Application
import com.vocalarm.app.alarm.AlarmWatchdog
import com.vocalarm.app.alarm.GuardianService
import com.vocalarm.app.alarm.NotificationChannels

class VocalarmApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.ensure(this)
        // Re-arm every enabled alarm: after a force stop or a data restore the system may not hold
        // any PendingIntent even though the alarm is still enabled in the file.
        container.scheduler.rescheduleAll()
        // Cheap, self-healing watchdog: repairs alarms a vendor cleaner dropped, even if the app
        // is never opened again before the next wake-up time.
        AlarmWatchdog.schedule(this)
        if (container.settingsRepository.settings.value.guardianEnabled) {
            // May be refused when this process was started in the background; MainActivity retries
            // the moment the user opens the app.
            GuardianService.start(this)
        }
    }
}
