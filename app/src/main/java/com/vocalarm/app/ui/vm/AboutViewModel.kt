package com.vocalarm.app.ui.vm

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.vocalarm.app.data.SettingsRepository
import com.vocalarm.app.update.UpdateRepository
import com.vocalarm.app.update.UpdateUiState
import kotlinx.coroutines.flow.StateFlow

class AboutViewModel(
    private val updates: UpdateRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<UpdateUiState> = updates.state
    val configured: Boolean = updates.isConfigured

    /** Checks at most once every six hours when the screen is opened. */
    fun checkOnOpen() {
        if (!configured) return
        val last = settings.settings.value.lastUpdateCheckAt
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        check()
    }

    fun check() {
        if (!configured) return
        settings.markUpdateChecked(System.currentTimeMillis())
        updates.check()
    }

    fun download() {
        updates.download()
    }

    fun cancelDownload() {
        updates.cancelDownload()
    }

    fun install() {
        updates.install()
    }

    fun installPermissionIntent(): Intent = updates.requestInstallPermissionIntent()

    private companion object {
        const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
