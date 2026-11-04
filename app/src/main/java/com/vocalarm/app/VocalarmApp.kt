package com.vocalarm.app

import android.app.Application
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
    }
}
