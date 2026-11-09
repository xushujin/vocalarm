package com.vocalarm.app.alarm

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.vocalarm.app.ui.ring.AlarmRingActivity

object AlarmPresentation {
    fun canOpenFromBackground(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())

    /** Explicit, immutable and used only for the app's own currently ringing alarm. */
    @Suppress("DEPRECATION")
    fun pendingIntent(context: Context, alarmId: String): PendingIntent {
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentCreatorBackgroundActivityStartMode(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                    } else {
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    },
                )
            }.toBundle()
        } else {
            null
        }
        return PendingIntent.getActivity(
            context,
            11,
            AlarmRingActivity.createIntent(context, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            options,
        )
    }

    /**
     * The optional overlay grant supplies Android's background-activity-launch exemption. Without
     * it this still works while our app is visible; a locked device uses the notification's FSI.
     * A successful call alone is not proof of display: the activity records actual window focus.
     */
    fun open(context: Context, alarmId: String) {
        runCatching { context.startActivity(AlarmRingActivity.createIntent(context, alarmId)) }
    }
}
