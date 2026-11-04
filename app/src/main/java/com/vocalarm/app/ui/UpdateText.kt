package com.vocalarm.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vocalarm.app.R

/** Stable error keys from the update layer are translated here, so no screen duplicates the map. */
@Composable
fun updateErrorText(reason: String): String = when {
    reason == "not-configured" -> stringResource(R.string.update_error_not_configured)
    reason.startsWith("http-404") -> stringResource(R.string.update_error_http_404)
    reason.startsWith("http-403") || reason.startsWith("http-429") ->
        stringResource(R.string.update_error_http_403)

    reason == "no-apk" -> stringResource(R.string.update_error_no_apk)
    reason == "apk-unreadable" -> stringResource(R.string.update_error_apk_unreadable)
    reason == "apk-package-mismatch" -> stringResource(R.string.update_error_apk_package_mismatch)
    reason == "install-permission" -> stringResource(R.string.update_error_install_permission)
    reason == "download-empty" -> stringResource(R.string.update_error_download_empty)
    reason == "download-incomplete" || reason == "incomplete" ->
        stringResource(R.string.update_error_download_incomplete)

    reason == "storage-unavailable" -> stringResource(R.string.update_error_storage_unavailable)
    reason == "network" -> stringResource(R.string.update_error_generic, reason)
    reason.startsWith("install-") -> stringResource(R.string.update_error_generic, reason)
    else -> stringResource(R.string.update_error_generic, reason)
}
