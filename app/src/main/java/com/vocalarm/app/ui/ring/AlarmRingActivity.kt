package com.vocalarm.app.ui.ring

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocalarm.app.VocalarmApp
import com.vocalarm.app.alarm.AlarmService
import com.vocalarm.app.ui.theme.Palettes
import com.vocalarm.app.ui.theme.VocalarmTheme

/**
 * Full-screen ringing surface, shown over the keyguard when the device is locked. All audio is
 * produced by AlarmService; this activity only presents and sends dismiss/snooze commands.
 */
class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepareLockScreenWindow()
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val container = (application as VocalarmApp).container
        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
            VocalarmTheme(palette = Palettes.byId(settings.paletteId)) {
                AlarmRingScreen(
                    container = container,
                    onFinish = { finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        confirmScreenVisible()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) confirmScreenVisible()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        confirmScreenVisible()
    }

    private fun confirmScreenVisible() {
        val container = (application as VocalarmApp).container
        if (window.decorView.hasWindowFocus() && container.ringing.current.value != null) {
            container.triggerLog.recordScreenShown()
            AlarmService.screenShown(this)
        }
    }

    @Suppress("DEPRECATION")
    private fun prepareLockScreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    companion object {
        private const val EXTRA_ALARM_ID = "extra_alarm_id"

        fun createIntent(context: Context, alarmId: String): Intent =
            Intent(context, AlarmRingActivity::class.java)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
