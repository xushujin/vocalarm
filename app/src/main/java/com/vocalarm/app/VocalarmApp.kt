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
        // Best-effort repair when the OS permits background jobs. Neither this job nor a
        // foreground service can bypass force-stop or vendor auto-start restrictions.
        AlarmWatchdog.schedule(this)
        if (container.settingsRepository.settings.value.guardianEnabled) {
            // May be refused when this process was started in the background; MainActivity retries
            // the moment the user opens the app.
            GuardianService.start(this)
        }
    }
}
