package com.vocalarm.app.update

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.text.format.Formatter
import androidx.core.content.ContextCompat
import com.vocalarm.app.AppContainer
import com.vocalarm.app.R
import com.vocalarm.app.VocalarmApp
import com.vocalarm.app.alarm.NotificationChannels
import com.vocalarm.app.ui.MainActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Downloads a release APK in a foreground service so progress survives the app going to the
 * background, then hands the file to the repository for validation and installation.
 */
class UpdateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var container: AppContainer
    private var job: Job? = null
    private var connection: HttpURLConnection? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as VocalarmApp).container
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                val version = intent.getStringExtra(EXTRA_VERSION).orEmpty()
                startForegroundCompat(buildProgressNotification(version, 0L, 0L, indeterminate = true))
                if (url.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (job == null) {
                    job = scope.launch { runDownload(url, version) }
                }
            }

            ACTION_CANCEL -> {
                job?.cancel()
                job = null
                runCatching { connection?.disconnect() }
                container.updateRepository.onCancelled()
                stopSelf()
            }

            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        job?.cancel()
        runCatching { connection?.disconnect() }
        container.updateRepository.onDownloadStopped()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runDownload(url: String, version: String) {
        val directory = File(cacheDir, "updates")
        if (!directory.exists() && !directory.mkdirs()) {
            fail("storage-unavailable", version)
            stopSelf()
            return
        }
        val target = File(directory, "vocalarm-$version-${System.currentTimeMillis()}.apk")
        val part = File(directory, "${target.name}.part")
        try {
            var lastPercent = -1
            download(url, part) { bytes, total ->
                container.updateRepository.onProgress(bytes, total)
                val percent = if (total > 0L) ((bytes * 100L) / total).toInt() else -1
                if (percent != lastPercent) {
                    lastPercent = percent
                    notifyProgress(version, bytes, total, indeterminate = total <= 0L)
                }
            }
            if (!part.isFile || part.length() <= 0L) throw IOException("download-empty")
            if (!part.renameTo(target)) throw IOException("rename-failed")
            container.updateRepository.onDownloaded(target)
            notifyReady(version)
            stopForeground(STOP_FOREGROUND_DETACH)
        } catch (cancelled: CancellationException) {
            part.delete()
            throw cancelled
        } catch (error: Exception) {
            part.delete()
            fail(error.message ?: "download-failed", version)
        } finally {
            job = null
            stopSelf()
        }
    }

    private suspend fun download(
        url: String,
        target: File,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ) {
        val http = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "Vocalarm-Android")
        }
        connection = http
        try {
            val code = http.responseCode
            if (code !in 200..299) throw IOException("http-$code")
            val total = http.contentLengthLong
            var downloaded = 0L
            http.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (currentCoroutineContext().isActive) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
            if (total > 0L && downloaded != total) throw IOException("download-incomplete")
        } finally {
            connection = null
            http.disconnect()
        }
    }

    private fun fail(reason: String, version: String) {
        container.updateRepository.onDownloadFailed(reason)
        notifyFailed(version)
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyProgress(version: String, bytes: Long, total: Long, indeterminate: Boolean) {
        val manager = getSystemService(android.app.NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildProgressNotification(version, bytes, total, indeterminate))
    }

    private fun notifyReady(version: String) {
        val notification = Notification.Builder(this, NotificationChannels.UPDATE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(getString(R.string.update_ready_title))
            .setContentText(getString(R.string.update_ready_text, version))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        getSystemService(android.app.NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notification)
    }

    private fun notifyFailed(version: String) {
        val notification = Notification.Builder(this, NotificationChannels.UPDATE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(getString(R.string.update_failed_title))
            .setContentText(getString(R.string.update_failed_text, version))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        getSystemService(android.app.NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildProgressNotification(
        version: String,
        bytes: Long,
        total: Long,
        indeterminate: Boolean,
    ): Notification {
        val cancelIntent = PendingIntent.getService(
            this,
            REQUEST_CANCEL,
            Intent(this, UpdateService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, NotificationChannels.UPDATE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(getString(R.string.update_downloading_title, version))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stat_download),
                    getString(R.string.action_cancel),
                    cancelIntent,
                ).build(),
            )
        if (indeterminate) {
            builder.setProgress(0, 0, true)
            builder.setContentText(getString(R.string.update_downloading_unknown))
        } else {
            val percent = if (total > 0L) ((bytes * 100L) / total).toInt() else 0
            builder.setProgress(100, percent, false)
            builder.setContentText(
                getString(
                    R.string.update_downloading_progress,
                    percent,
                    Formatter.formatShortFileSize(this, bytes),
                    Formatter.formatShortFileSize(this, total),
                ),
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN_APP,
        Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_ABOUT)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_DOWNLOAD = "com.vocalarm.app.action.DOWNLOAD_UPDATE"
        const val ACTION_CANCEL = "com.vocalarm.app.action.CANCEL_UPDATE"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_VERSION = "extra_version"
        private const val NOTIFICATION_ID = 2001
        private const val REQUEST_CANCEL = 41
        private const val REQUEST_OPEN_APP = 42

        fun start(context: Context, url: String, version: String) {
            val intent = Intent(context, UpdateService::class.java)
                .setAction(ACTION_DOWNLOAD)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_VERSION, version)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, UpdateService::class.java).setAction(ACTION_CANCEL)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}
