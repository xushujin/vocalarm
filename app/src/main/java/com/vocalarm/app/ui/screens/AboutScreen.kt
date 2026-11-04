package com.vocalarm.app.ui.screens

import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vocalarm.app.AppContainer
import com.vocalarm.app.BuildConfig
import com.vocalarm.app.R
import com.vocalarm.app.ui.NeuProgressBar
import com.vocalarm.app.ui.TabBarSpacer
import com.vocalarm.app.ui.neu.NeuButton
import com.vocalarm.app.ui.neu.NeuCard
import com.vocalarm.app.ui.neu.NeuTone
import com.vocalarm.app.ui.neu.VIcon
import com.vocalarm.app.ui.neu.VIconImage
import com.vocalarm.app.ui.neu.neuSurface
import com.vocalarm.app.ui.theme.LocalPalette
import com.vocalarm.app.ui.updateErrorText
import com.vocalarm.app.update.ApkInstaller
import com.vocalarm.app.update.UpdateUiState
import com.vocalarm.app.ui.vm.AboutViewModel

@Composable
fun AboutScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val viewModel: AboutViewModel = viewModel(
        initializer = { AboutViewModel(container.updateRepository, container.settingsRepository) },
    )
    val updateState by viewModel.state.collectAsStateWithLifecycle()
    var canInstall by rememberSaveable { mutableStateOf(true) }
    var checkedOnce by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { canInstall = ApkInstaller.canInstall(context) },
    )

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canInstall = ApkInstaller.canInstall(context)
    }
    LaunchedEffect(Unit) {
        if (!checkedOnce) {
            checkedOnce = true
            viewModel.checkOnOpen()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.about_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                )
            }
        }
        item { AboutHeaderCard() }
        item { InfoCard(title = stringResource(R.string.about_section_stack), body = stringResource(R.string.about_stack_value)) }
        item { InfoCard(title = stringResource(R.string.about_section_support), body = stringResource(R.string.about_support_value)) }
        item { PermissionsCard() }
        item {
            UpdateCard(
                state = updateState,
                configured = viewModel.configured,
                canInstall = canInstall,
                onCheck = viewModel::check,
                onDownload = viewModel::download,
                onCancel = viewModel::cancelDownload,
                onInstall = viewModel::install,
                onRequestPermission = { permissionLauncher.launch(viewModel.installPermissionIntent()) },
            )
        }
        item { SourceNoteCard() }
        item { TabBarSpacer() }
    }

}

@Composable
private fun AboutHeaderCard() {
    val palette = LocalPalette.current
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 28.dp,
        depth = 9.dp,
        contentPadding = PaddingValues(22.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .neuSurface(
                        palette = palette,
                        fill = palette.background,
                        radius = 38.dp,
                        tone = NeuTone.Raised,
                        depth = 6.dp,
                        blur = 14.dp,
                        circle = true,
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                VIconImage(icon = VIcon.Bell, tint = palette.accent, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = palette.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.about_source_note),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    val palette = LocalPalette.current
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 24.dp,
        depth = 6.dp,
        contentPadding = PaddingValues(20.dp),
    ) {
        Column {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = palette.muted)
            Spacer(Modifier.height(8.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
        }
    }
}

@Composable
private fun PermissionsCard() {
    val palette = LocalPalette.current
    val permissions = listOf(
        R.string.perm_mic,
        R.string.perm_notifications,
        R.string.perm_exact_alarm,
        R.string.perm_foreground,
        R.string.perm_boot,
        R.string.perm_wake,
        R.string.perm_install,
        R.string.perm_internet,
    )
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 24.dp,
        depth = 6.dp,
        contentPadding = PaddingValues(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.about_section_permissions),
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
            )
            permissions.forEach { res ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(palette.accent),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(res),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateCard(
    state: UpdateUiState,
    configured: Boolean,
    canInstall: Boolean,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 24.dp,
        depth = 6.dp,
        contentPadding = PaddingValues(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VIconImage(icon = VIcon.Download, tint = palette.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.about_section_update),
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.about_update_current, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
            }

            if (!configured) {
                Text(
                    text = stringResource(R.string.about_update_not_configured),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
                return@Column
            }

            Text(
                text = stringResource(R.string.about_update_source, BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )

            when (state) {
                is UpdateUiState.Idle -> {
                    NeuButton(
                        text = stringResource(R.string.about_update_check),
                        onClick = onCheck,
                        icon = VIcon.Refresh,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is UpdateUiState.Checking -> {
                    Text(
                        text = stringResource(R.string.about_update_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                    )
                }

                is UpdateUiState.UpToDate -> {
                    Text(
                        text = stringResource(R.string.about_update_latest, state.latestVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                    )
                    NeuButton(
                        text = stringResource(R.string.action_retry),
                        onClick = onCheck,
                        icon = VIcon.Refresh,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is UpdateUiState.Available -> {
                    Text(
                        text = stringResource(R.string.about_update_available, state.release.version),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.accent,
                    )
                    if (state.release.notes.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.about_update_notes),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                        )
                        Text(
                            text = state.release.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    NeuButton(
                        text = stringResource(R.string.about_update_download),
                        onClick = onDownload,
                        icon = VIcon.Download,
                        accent = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is UpdateUiState.Downloading -> {
                    Text(
                        text = stringResource(
                            R.string.about_update_downloading,
                            (state.progress * 100).toInt(),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                    )
                    NeuProgressBar(progress = state.progress)
                    if (state.total > 0L) {
                        Text(
                            text = stringResource(
                                R.string.about_update_download_size,
                                Formatter.formatShortFileSize(context, state.bytes),
                                Formatter.formatShortFileSize(context, state.total),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                        )
                    }
                    NeuButton(
                        text = stringResource(R.string.about_update_cancel),
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is UpdateUiState.ReadyToInstall -> {
                    Text(
                        text = stringResource(R.string.about_update_available, state.release.version),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.accent,
                    )
                    if (!canInstall) {
                        Text(
                            text = stringResource(R.string.about_update_permission_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                        )
                        NeuButton(
                            text = stringResource(R.string.home_open_settings),
                            onClick = onRequestPermission,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        NeuButton(
                            text = stringResource(R.string.about_update_install),
                            onClick = onInstall,
                            icon = VIcon.Download,
                            accent = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                is UpdateUiState.Installing -> {
                    Text(
                        text = stringResource(R.string.about_update_installing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                    )
                }

                is UpdateUiState.Installed -> {
                    Text(
                        text = stringResource(R.string.about_update_installed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.accent,
                    )
                }

                is UpdateUiState.Failed -> {
                    Text(
                        text = stringResource(R.string.about_update_failed, updateErrorText(state.reason)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.danger,
                    )
                    NeuButton(
                        text = stringResource(R.string.action_retry),
                        onClick = onCheck,
                        icon = VIcon.Refresh,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceNoteCard() {
    val palette = LocalPalette.current
    val context = LocalContext.current
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 22.dp,
        depth = 5.dp,
        contentPadding = PaddingValues(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.about_tech_note),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
            )
            if (BuildConfig.GITHUB_OWNER.isNotBlank() && BuildConfig.GITHUB_REPO.isNotBlank()) {
                NeuButton(
                    text = stringResource(R.string.about_repo),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}".toUri(),
                                ),
                            )
                        }
                    },
                    icon = VIcon.Info,
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp,
                )
            }
        }
    }
}
