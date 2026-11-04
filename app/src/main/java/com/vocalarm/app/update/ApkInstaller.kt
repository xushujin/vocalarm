package com.vocalarm.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.vocalarm.app.BuildConfig
import java.io.File

sealed interface ApkValidation {
    data object Valid : ApkValidation
    data class Invalid(val reason: String) : ApkValidation
    data class SameOrOlder(val versionCode: Long) : ApkValidation
}

/**
 * Verifies that the downloaded file is a readable APK of this package with a higher version code.
 * This is the binary-format check that protects the installer from truncated or foreign files.
 */
object ApkValidator {

    fun validate(context: Context, file: File): ApkValidation {
        val info = archiveInfo(context, file) ?: return ApkValidation.Invalid("apk-unreadable")
        if (info.packageName != context.packageName) {
            return ApkValidation.Invalid("apk-package-mismatch")
        }
        val code = versionCode(info)
        if (code <= BuildConfig.VERSION_CODE.toLong()) {
            return ApkValidation.SameOrOlder(code)
        }
        return ApkValidation.Valid
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(context: Context, file: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(0L),
            )
        } else {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
}

object ApkInstaller {

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun settingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        )

    fun install(context: Context, file: File): Result<Unit> {
        val viaSession = runCatching { installWithSession(context, file) }
        if (viaSession.isSuccess) return Result.success(Unit)
        return runCatching { installWithIntent(context, file) }
    }

    private fun installWithSession(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstallerParams.create()
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("vocalarm.apk", 0L, file.length()).use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    session.fsync(output)
                }
            }
            val resultIntent = Intent(context, InstallResultReceiver::class.java)
                .setPackage(context.packageName)
            val flags = PendingIntentFlags.create()
            val pending = android.app.PendingIntent.getBroadcast(context, sessionId, resultIntent, flags)
            session.commit(pending.intentSender)
        }
    }

    private fun installWithIntent(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private object PackageInstallerParams {
    fun create(): android.content.pm.PackageInstaller.SessionParams =
        android.content.pm.PackageInstaller.SessionParams(
            android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        )
}

private object PendingIntentFlags {
    fun create(): Int =
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.app.PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
}
