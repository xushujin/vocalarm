package com.vocalarm.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.vocalarm.app.BuildConfig
import com.vocalarm.app.domain.VersionComparator
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val latestVersion: String) : UpdateUiState
    data class Available(val release: ReleaseInfo) : UpdateUiState
    data class Downloading(val version: String, val bytes: Long, val total: Long) : UpdateUiState {
        val progress: Float
            get() = if (total <= 0L) 0f else (bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    data class ReadyToInstall(val release: ReleaseInfo, val file: File) : UpdateUiState
    data object Installing : UpdateUiState
    data object Installed : UpdateUiState
    data class Failed(val reason: String) : UpdateUiState
}

/**
 * Owns update state so the About screen and the download service never disagree. Error reasons are
 * stable keys translated in the UI layer.
 */
class UpdateRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val api: UpdateApi,
) {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var activeRelease: ReleaseInfo? = null

    val isConfigured: Boolean get() = api.isConfigured

    fun check() {
        // Never discard a downloaded APK or an in-flight install by re-checking.
        when (_state.value) {
            is UpdateUiState.Checking,
            is UpdateUiState.Downloading,
            is UpdateUiState.ReadyToInstall,
            is UpdateUiState.Installing,
            -> return

            else -> Unit
        }
        if (!api.isConfigured) {
            _state.value = UpdateUiState.Failed("not-configured")
            return
        }
        _state.value = UpdateUiState.Checking
        scope.launch {
            api.latestRelease()
                .onSuccess { release ->
                    activeRelease = release
                    val current = BuildConfig.VERSION_NAME
                    _state.value = if (VersionComparator.isNewer(release.version, current)) {
                        UpdateUiState.Available(release)
                    } else {
                        UpdateUiState.UpToDate(release.version)
                    }
                }
                .onFailure { error ->
                    _state.value = UpdateUiState.Failed(error.message ?: "network")
                }
        }
    }

    fun download() {
        val release = (_state.value as? UpdateUiState.Available)?.release ?: return
        val asset = release.apk
        if (asset == null) {
            _state.value = UpdateUiState.Failed("no-apk")
            return
        }
        activeRelease = release
        _state.value = UpdateUiState.Downloading(release.version, 0L, asset.size)
        UpdateService.start(context, asset.downloadUrl, release.version)
    }

    fun cancelDownload() {
        UpdateService.cancel(context)
        val release = activeRelease
        _state.value = if (release != null) UpdateUiState.Available(release) else UpdateUiState.Idle
    }

    fun install() {
        val ready = _state.value as? UpdateUiState.ReadyToInstall ?: return
        if (!ApkInstaller.canInstall(context)) {
            _state.value = UpdateUiState.Failed("install-permission")
            return
        }
        val result = ApkInstaller.install(context, ready.file)
        _state.value = if (result.isSuccess) {
            UpdateUiState.Installing
        } else {
            UpdateUiState.Failed(result.exceptionOrNull()?.message ?: "install-failed")
        }
    }

    fun requestInstallPermissionIntent(): Intent = ApkInstaller.settingsIntent(context)

    fun onProgress(bytes: Long, total: Long) {
        val current = _state.value
        if (current is UpdateUiState.Downloading) {
            _state.value = current.copy(bytes = bytes, total = total.coerceAtLeast(current.total))
        } else if (current is UpdateUiState.Available) {
            _state.value = UpdateUiState.Downloading(current.release.version, bytes, total)
        }
    }

    fun onDownloaded(file: File) {
        val release = activeRelease
        if (release == null || !file.isFile || file.length() <= 0L) {
            file.delete()
            _state.value = UpdateUiState.Failed("download-empty")
            return
        }
        val validation = ApkValidator.validate(context, file)
        when (validation) {
            is ApkValidation.Invalid -> {
                file.delete()
                _state.value = UpdateUiState.Failed(validation.reason)
            }

            is ApkValidation.SameOrOlder -> {
                file.delete()
                _state.value = UpdateUiState.UpToDate(release.version)
            }

            ApkValidation.Valid -> _state.value = UpdateUiState.ReadyToInstall(release, file)
        }
    }

    fun onDownloadFailed(reason: String) {
        _state.value = UpdateUiState.Failed(reason)
    }

    fun onCancelled() {
        val release = activeRelease
        _state.value = if (release != null) UpdateUiState.Available(release) else UpdateUiState.Idle
    }

    fun onInstalled() {
        _state.value = UpdateUiState.Installed
    }

    fun onInstallFailed(reason: String) {
        _state.value = UpdateUiState.Failed(reason)
    }

    /** Called when the download service goes away; keeps a usable state instead of a stuck bar. */
    fun onDownloadStopped() {
        if (_state.value !is UpdateUiState.Downloading) return
        val release = activeRelease
        _state.value = if (release != null) UpdateUiState.Available(release) else UpdateUiState.Idle
    }
}
