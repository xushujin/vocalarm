package com.vocalarm.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.vocalarm.app.VocalarmApp

/** Receives the PackageInstaller result, including the user-action confirmation intent. */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repository = (context.applicationContext as? VocalarmApp)?.container?.updateRepository ?: return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = userActionIntent(intent)
                if (confirmation != null) {
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirmation) }
                } else {
                    repository.onInstallFailed("install-confirmation-missing")
                }
            }

            PackageInstaller.STATUS_SUCCESS -> repository.onInstalled()

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                repository.onInstallFailed(message?.take(160) ?: "install-failed")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun userActionIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
}
