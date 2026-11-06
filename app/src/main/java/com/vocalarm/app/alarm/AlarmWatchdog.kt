package com.vocalarm.app.alarm

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.vocalarm.app.VocalarmApp

/**
 * Periodic safety net: every 15 minutes the system wakes us for a few milliseconds and we put every
 * enabled alarm back into AlarmManager. Vendor cleaners that drop alarms together with the app are
 * repaired long before the next wake-up time, without any user interaction.
 */
class AlarmWatchdogJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        runCatching {
            (application as VocalarmApp).container.scheduler.rescheduleAll()
        }
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}

object AlarmWatchdog {

    private const val JOB_ID = 7301
    private const val INTERVAL_MS = 15 * 60 * 1000L

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (runCatching { scheduler.getPendingJob(JOB_ID) }.getOrNull() != null) return
        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(context, AlarmWatchdogJobService::class.java),
        )
            .setPeriodic(INTERVAL_MS)
            .setPersisted(true)
            .build()
        runCatching { scheduler.schedule(job) }
    }

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
    }
}
