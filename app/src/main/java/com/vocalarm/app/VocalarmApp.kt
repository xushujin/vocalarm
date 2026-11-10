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
        // Do not replace alarms while the OS is starting this process to deliver one. Recovery is
        // explicit in the launcher, boot/clock receiver, task-removal callback and watchdog.
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
