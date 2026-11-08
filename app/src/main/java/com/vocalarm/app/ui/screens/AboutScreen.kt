package com.vocalarm.app.ui.screens

import android.app.AlarmManager
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vocalarm.app.AppContainer
import com.vocalarm.app.BuildConfig
import com.vocalarm.app.R
import com.vocalarm.app.alarm.GuardianService
import com.vocalarm.app.alarm.NotificationChannels
import com.vocalarm.app.domain.AlarmTime
import com.vocalarm.app.ui.Eyebrow
import com.vocalarm.app.ui.NeuProgressBar
import com.vocalarm.app.ui.StatusPill
import com.vocalarm.app.ui.TabBarSpacer
import com.vocalarm.app.ui.VocalarmMark
import com.vocalarm.app.ui.neu.NeuButton
import com.vocalarm.app.ui.neu.NeuCard
import com.vocalarm.app.ui.neu.NeuDivider
import com.vocalarm.app.ui.neu.NeuIconTile
import com.vocalarm.app.ui.neu.VIcon
import com.vocalarm.app.ui.neu.VIconImage
import com.vocalarm.app.ui.theme.LocalPalette
import com.vocalarm.app.ui.updateErrorText
import com.vocalarm.app.update.ApkInstaller
import com.vocalarm.app.update.UpdateUiState
import com.vocalarm.app.ui.vm.AboutViewModel
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale

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
                Eyebrow(text = "VOCALARM", color = palette.accent)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.about_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                    color = palette.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
            }
        }

        item { AboutHeroCard() }
        item {
            InfoCard(
                icon = VIcon.Droplet,
                title = stringResource(R.string.about_section_stack),
                body = stringResource(R.string.about_stack_value),
            )
        }
        item {
            InfoCard(
                icon = VIcon.Bell,
                title = stringResource(R.string.about_section_support),
                body = stringResource(R.string.about_support_value),
            )
        }
        item { PermissionsCard() }
        item { DiagnosticsCard(container) }
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
        item {
            Text(
                text = stringResource(R.string.about_footer),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                textAlign = TextAlign.Center,
            )
        }
        item { TabBarSpacer() }
    }
}

@Composable
private fun AboutHeroCard() {
    val palette = LocalPalette.current
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 32.dp,
        depth = 12.dp,
        contentPadding = PaddingValues(24.dp),
        glow = palette.accent.copy(alpha = 0.28f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            VocalarmMark(size = 96.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                color = palette.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            StatusPill(
                icon = VIcon.Info,
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.about_source_note),
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(stringResource(R.string.about_stat_themes))
                StatPill(stringResource(R.string.about_stat_local))
                StatPill(stringResource(R.string.about_stat_no_account))
            }
        }
    }
}

@Composable
private fun StatPill(text: String) {
    val palette = LocalPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surfaceHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
        )
    }
}

@Composable
private fun InfoCard(icon: VIcon, title: String, body: String) {
    val palette = LocalPalette.current
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 24.dp,
        depth = 6.dp,
        contentPadding = PaddingValues(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeuIconTile(icon = icon, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
            )
        }
    }
}

@Composable
private fun PermissionsCard() {
    val palette = LocalPalette.current
    val permissions = listOf(
        R.string.perm_mic,
        R.string.perm_notifications,
        R.string.perm_full_screen,
        R.string.perm_exact_alarm,
        R.string.perm_battery,
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
        contentPadding = PaddingValues(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeuIconTile(icon = VIcon.Info, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.about_section_permissions),
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                )
            }
            Spacer(Modifier.height(12.dp))
            permissions.forEachIndexed { index, res ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VIconImage(
                        icon = VIcon.Check,
                        tint = palette.accent,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = stringResource(res),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
                if (index != permissions.lastIndex) {
                    Spacer(Modifier.height(9.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            NeuDivider()
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.about_tech_note),
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
        }
    }
}

@Composable
private fun DiagnosticsCard(container: AppContainer) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val alarmManager = remember { context.getSystemService(AlarmManager::class.java) }
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }

    var appNext by remember { mutableLongStateOf(0L) }
    var systemNext by remember { mutableLongStateOf(0L) }
    var deliveredAt by remember { mutableLongStateOf(0L) }
    var ringingAt by remember { mutableLongStateOf(0L) }
    var deliveredCount by remember { mutableIntStateOf(0) }
    var exact by remember { mutableStateOf(true) }
    var notifications by remember { mutableStateOf(true) }
    var fullScreen by remember { mutableStateOf(true) }
    var batteryLimited by remember { mutableStateOf(false) }
    var guardianOn by remember { mutableStateOf(true) }
    var guardianRunning by remember { mutableStateOf(false) }
    val isOplusDevice = remember {
        Build.MANUFACTURER.lowercase(Locale.ROOT) in setOf("oppo", "oneplus", "realme", "oplus")
    }
    val testMissingText = stringResource(R.string.diag_test_missing)
    val testToastText = stringResource(R.string.diag_test_toast)
    val testFailedText = stringResource(R.string.diag_test_failed)

    fun refresh() {
        val snapshot = container.triggerLog.read()
        deliveredAt = snapshot.lastDeliveredAt
        ringingAt = snapshot.lastRingingAt
        deliveredCount = snapshot.deliveredCount
        val now = System.currentTimeMillis()
        appNext = container.alarmRepository.alarms.value
            .filter { it.enabled }
            .mapNotNull { AlarmTime.nextTriggerMillis(it, now, ZoneId.systemDefault()) }
            .minOrNull() ?: 0L
        systemNext = alarmManager?.nextAlarmClock?.triggerTime ?: 0L
        exact = container.scheduler.canScheduleExact()
        notifications = NotificationChannels.areNotificationsEnabled(context)
        fullScreen = NotificationChannels.canUseFullScreenIntent(context)
        batteryLimited = powerManager?.isIgnoringBatteryOptimizations(context.packageName) != true
        guardianOn = container.settingsRepository.settings.value.guardianEnabled
        guardianRunning = GuardianService.running
    }

    LaunchedEffect(Unit) { refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 24.dp,
        depth = 6.dp,
        contentPadding = PaddingValues(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeuIconTile(icon = VIcon.Info, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.about_section_diagnostics),
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                )
            }
            Spacer(Modifier.height(10.dp))
            DiagRow(
                label = stringResource(R.string.diag_app_next),
                value = if (appNext > 0L) formatStamp(appNext) else stringResource(R.string.diag_none),
                ok = appNext > 0L,
            )
            DiagRow(
                label = stringResource(R.string.diag_system_next),
                value = if (systemNext > 0L) formatStamp(systemNext) else stringResource(R.string.diag_none),
                ok = null,
            )
            DiagRow(
                label = stringResource(R.string.diag_last_delivery),
                value = if (deliveredAt > 0L) {
                    stringResource(R.string.diag_delivery_count, deliveredCount) + " · " +
                        formatStamp(deliveredAt, withSeconds = true)
                } else {
                    stringResource(R.string.diag_never)
                },
                ok = deliveredAt > 0L,
            )
            DiagRow(
                label = stringResource(R.string.diag_last_ringing),
                value = if (ringingAt > 0L) {
                    formatStamp(ringingAt, withSeconds = true)
                } else {
                    stringResource(R.string.diag_never)
                },
                ok = null,
            )
            DiagRow(
                label = stringResource(R.string.diag_exact),
                value = stringResource(if (exact) R.string.diag_granted else R.string.diag_denied),
                ok = exact,
            )
            DiagRow(
                label = stringResource(R.string.diag_notifications),
                value = stringResource(if (notifications) R.string.diag_enabled else R.string.diag_disabled),
                ok = notifications,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                DiagRow(
                    label = stringResource(R.string.diag_full_screen),
                    value = stringResource(if (fullScreen) R.string.diag_enabled else R.string.diag_disabled),
                    ok = fullScreen,
                )
            }
            DiagRow(
                label = stringResource(R.string.diag_battery),
                value = stringResource(
                    if (batteryLimited) R.string.diag_battery_limited else R.string.diag_battery_ok,
                ),
                ok = !batteryLimited,
            )
            DiagRow(
                label = stringResource(R.string.diag_guardian),
                value = stringResource(
                    when {
                        guardianRunning -> R.string.diag_guardian_running
                        guardianOn -> R.string.diag_guardian_on
                        else -> R.string.diag_guardian_off
                    },
                ),
                ok = guardianRunning,
            )
            Spacer(Modifier.height(8.dp))
            NeuButton(
                text = stringResource(
                    if (guardianOn) R.string.guardian_disable else R.string.guardian_enable,
                ),
                onClick = {
                    val next = !guardianOn
                    container.settingsRepository.setGuardianEnabled(next)
                    guardianOn = next
                    if (next) GuardianService.start(context) else GuardianService.stop(context)
                    guardianRunning = GuardianService.running
                },
                modifier = Modifier.fillMaxWidth(),
                height = 42.dp,
            )
            if (batteryLimited) {
                Spacer(Modifier.height(8.dp))
                NeuButton(
                    text = stringResource(R.string.diag_battery_action),
                    onClick = { requestIgnoreBatteryOptimizations(context) },
                    modifier = Modifier.fillMaxWidth(),
                    height = 42.dp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (isOplusDevice) R.string.diag_background_oplus else R.string.diag_background_generic,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            NeuButton(
                text = stringResource(R.string.diag_app_settings),
                onClick = { openAppSettings(context) },
                modifier = Modifier.fillMaxWidth(),
                height = 42.dp,
            )
            Spacer(Modifier.height(8.dp))
            NeuButton(
                text = stringResource(R.string.diag_test),
                onClick = {
                    val target = container.alarmRepository.alarms.value
                        .firstOrNull { it.enabled }
                        ?: container.alarmRepository.alarms.value.firstOrNull()
                    if (target == null) {
                        Toast.makeText(context, testMissingText, Toast.LENGTH_SHORT).show()
                    } else {
                        val scheduled = container.scheduler.scheduleTest(target.id, TEST_DELAY_MS)
                        Toast.makeText(
                            context,
                            if (scheduled) testToastText else testFailedText,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                height = 42.dp,
            )
            Spacer(Modifier.height(10.dp))
            NeuDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.diag_hint),
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String, ok: Boolean?) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (ok == false) palette.danger else palette.textPrimary,
            textAlign = TextAlign.End,
        )
    }
}

private fun formatStamp(millis: Long, withSeconds: Boolean = false): String =
    SimpleDateFormat(
        if (withSeconds) "M月d日 HH:mm:ss" else "M月d日 HH:mm",
        Locale.getDefault(),
    ).format(Date(millis))

private const val TEST_DELAY_MS = 10_000L

/**
 * Requests Android's Doze exemption. Vendor auto-start and cleanup controls are separate settings.
 */
@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:${context.packageName}".toUri(),
            ),
        )
    }.recoverCatching {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }.onFailure {
        Toast.makeText(context, R.string.diag_settings_unavailable, Toast.LENGTH_LONG).show()
    }
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
        )
    }.recoverCatching {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }.onFailure {
        Toast.makeText(context, R.string.diag_settings_unavailable, Toast.LENGTH_LONG).show()
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
        contentPadding = PaddingValues(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeuIconTile(icon = VIcon.Download, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.about_section_update),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = stringResource(R.string.about_update_current, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            if (!configured) {
                Text(
                    text = stringResource(R.string.about_update_not_configured),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
                return@Column
            }

            Text(
                text = stringResource(R.string.about_update_source, BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )
            Spacer(Modifier.height(10.dp))

            when (state) {
                is UpdateUiState.Idle -> {
                    NeuButton(
                        text = stringResource(R.string.about_update_check),
                        onClick = onCheck,
                        icon = VIcon.Refresh,
                        modifier = Modifier.fillMaxWidth(),
                        height = 46.dp,
                    )
                }

                is UpdateUiState.Checking -> {
                    Text(
                        text = stringResource(R.string.about_update_checking),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }

                is UpdateUiState.UpToDate -> {
                    Text(
                        text = stringResource(R.string.about_update_latest, state.latestVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    NeuButton(
                        text = stringResource(R.string.action_retry),
                        onClick = onCheck,
                        icon = VIcon.Refresh,
                        modifier = Modifier.fillMaxWidth(),
                        height = 46.dp,
                    )
                }

                is UpdateUiState.Available -> {
                    Text(
                        text = stringResource(R.string.about_update_available, state.release.version),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.accent,
                    )
                    if (state.release.notes.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = state.release.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            maxLines = 7,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    NeuButton(
                        text = stringResource(R.string.about_update_download),
                        onClick = onDownload,
                        icon = VIcon.Download,
                        accent = true,
                        modifier = Modifier.fillMaxWidth(),
                        height = 46.dp,
                    )
                }

                is UpdateUiState.Downloading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(
                                R.string.about_update_downloading,
                                (state.progress * 100).toInt(),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary,
                        )
                        Spacer(Modifier.weight(1f))
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
                    }
                    Spacer(Modifier.height(10.dp))
                    NeuProgressBar(progress = state.progress)
                    Spacer(Modifier.height(12.dp))
                    NeuButton(
                        text = stringResource(R.string.about_update_cancel),
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        height = 46.dp,
                    )
                }

                is UpdateUiState.ReadyToInstall -> {
                    Text(
                        text = stringResource(R.string.about_update_available, state.release.version),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.accent,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (!canInstall) {
                        Text(
                            text = stringResource(R.string.about_update_permission_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        NeuButton(
                            text = stringResource(R.string.home_open_settings),
                            onClick = onRequestPermission,
                            modifier = Modifier.fillMaxWidth(),
                            height = 46.dp,
                        )
                    } else {
                        NeuButton(
                            text = stringResource(R.string.about_update_install),
                            onClick = onInstall,
                            icon = VIcon.Download,
                            accent = true,
                            modifier = Modifier.fillMaxWidth(),
                            height = 46.dp,
                        )
                    }
                }

                is UpdateUiState.Installing -> {
                    Text(
                        text = stringResource(R.string.about_update_installing),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }

                is UpdateUiState.Installed -> {
                    Text(
                        text = stringResource(R.string.about_update_installed),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.accent,
                    )
                }

                is UpdateUiState.Failed -> {
                    Text(
                        text = stringResource(R.string.about_update_failed, updateErrorText(state.reason)),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.danger,
                    )
                    Spacer(Modifier.height(10.dp))
                    NeuButton(
                        text = stringResource(R.string.action_retry),
                        onClick = onCheck,
                        icon = VIcon.Refresh,
                        modifier = Modifier.fillMaxWidth(),
                        height = 46.dp,
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
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.about_source_note),
                style = MaterialTheme.typography.bodySmall,
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
                    height = 46.dp,
                )
            }
        }
    }
}
