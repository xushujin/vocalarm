package com.vocalarm.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vocalarm.app.AppContainer
import com.vocalarm.app.R
import com.vocalarm.app.alarm.NotificationChannels
import com.vocalarm.app.domain.Alarm
import com.vocalarm.app.domain.StoreIssue
import com.vocalarm.app.ui.AlarmTimeText
import com.vocalarm.app.ui.contentSummary
import com.vocalarm.app.ui.NeuBanner
import com.vocalarm.app.ui.NeuDialog
import com.vocalarm.app.ui.remainingText
import com.vocalarm.app.ui.repeatText
import com.vocalarm.app.ui.TabBarSpacer
import com.vocalarm.app.ui.neu.NeuButton
import com.vocalarm.app.ui.neu.NeuCard
import com.vocalarm.app.ui.neu.NeuIconButton
import com.vocalarm.app.ui.neu.NeuSwitch
import com.vocalarm.app.ui.neu.VIcon
import com.vocalarm.app.ui.neu.VIconImage
import com.vocalarm.app.ui.theme.LocalPalette
import com.vocalarm.app.ui.vm.AlarmListViewModel

@Composable
fun AlarmListScreen(
    container: AppContainer,
    onEdit: (String?) -> Unit,
    onOpenAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val viewModel: AlarmListViewModel = viewModel(
        initializer = {
            AlarmListViewModel(
                repository = container.alarmRepository,
                scheduler = container.scheduler,
                recordings = container.recordings,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<Alarm?>(null) }
    var notificationsEnabled by rememberSaveable { mutableStateOf(true) }
    var exactAlarmsEnabled by rememberSaveable { mutableStateOf(true) }
    var requestedNotifications by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> notificationsEnabled = NotificationChannels.areNotificationsEnabled(context) && granted },
    )

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsEnabled = NotificationChannels.areNotificationsEnabled(context)
        exactAlarmsEnabled = container.scheduler.canScheduleExact()
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!requestedNotifications && !permissionGranted) {
            requestedNotifications = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HomeHeader(onOpenAppearance = onOpenAppearance)
            }
            if (state.nextTriggerAt != null && state.nextAlarm != null) {
                item {
                    NextAlarmCard(
                        alarm = state.nextAlarm!!,
                        triggerAt = state.nextTriggerAt!!,
                        now = state.now,
                    )
                }
            }
            if (!state.readOnly) {
                if (!notificationsEnabled) {
                    item {
                        NeuBanner(
                            text = stringResource(R.string.home_notifications_off),
                            actionText = stringResource(R.string.home_open_settings),
                            onAction = {
                                runCatching {
                                    context.startActivity(NotificationChannels.notificationSettingsAction(context))
                                }
                            },
                        )
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmsEnabled) {
                    item {
                        NeuBanner(
                            text = stringResource(R.string.home_exact_alarm_off),
                            actionText = stringResource(R.string.home_open_settings),
                            onAction = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            "package:${context.packageName}".toUri(),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
            }
            state.issue?.let { issue ->
                item {
                    StoreIssueBanner(issue = issue, onDismiss = viewModel::clearIssue)
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.tab_alarms),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${state.alarms.count { it.enabled }}/${state.alarms.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted,
                    )
                }
            }
            if (state.alarms.isEmpty()) {
                item {
                    EmptyState(
                        enabled = !state.readOnly,
                        onCreate = { onEdit(null) },
                    )
                }
            } else {
                items(items = state.alarms, key = { it.id }) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        onToggle = { enabled -> viewModel.setEnabled(alarm, enabled) },
                        onClick = if (state.readOnly) null else ({ onEdit(alarm.id) }),
                        switchEnabled = !state.readOnly,
                    )
                }
            }
            item { TabBarSpacer() }
        }

        if (state.alarms.isNotEmpty() && !state.readOnly) {
            NeuIconButton(
                icon = VIcon.Plus,
                contentDescription = stringResource(R.string.home_new_alarm),
                onClick = { onEdit(null) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 26.dp, bottom = 126.dp),
                size = 62.dp,
                accent = true,
            )
        }
    }

    pendingDelete?.let { alarm ->
        NeuDialog(
            title = stringResource(R.string.home_delete_title),
            body = stringResource(R.string.home_delete_body),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = {
                viewModel.delete(alarm)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun HomeHeader(onOpenAppearance: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_greeting),
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted,
            )
        }
        NeuIconButton(
            icon = VIcon.Droplet,
            contentDescription = stringResource(R.string.tab_appearance),
            onClick = onOpenAppearance,
            size = 46.dp,
        )
    }
}

@Composable
private fun NextAlarmCard(alarm: Alarm, triggerAt: Long, now: Long) {
    val palette = LocalPalette.current
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 28.dp,
        depth = 9.dp,
        contentPadding = PaddingValues(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VIconImage(
                    icon = VIcon.Clock,
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.home_next_alarm),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted,
                )
                Spacer(Modifier.weight(1f))
                remainingText(triggerAt = triggerAt, now = now)?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.accent,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                AlarmTimeText(alarm = alarm, fontSize = 54.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(
                        text = alarm.label.ifBlank { stringResource(R.string.alarm_default_label) },
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = repeatText(alarm.repeatDays) + " · " + contentSummary(alarm),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    onToggle: (Boolean) -> Unit,
    onClick: (() -> Unit)?,
    switchEnabled: Boolean,
) {
    val palette = LocalPalette.current
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 24.dp,
        depth = 6.dp,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = alarm.timeText,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 38.sp,
                            lineHeight = 42.sp,
                        ),
                        color = if (alarm.enabled) palette.textPrimary else palette.muted,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (alarm.enabled) {
                            stringResource(R.string.home_alarm_on)
                        } else {
                            stringResource(R.string.home_alarm_off)
                        },
                        modifier = Modifier.padding(bottom = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (alarm.enabled) palette.accent else palette.muted,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(repeatText(alarm.repeatDays))
                        append(" · ")
                        append(contentSummary(alarm))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (alarm.label.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = alarm.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            NeuSwitch(checked = alarm.enabled, onCheckedChange = onToggle, enabled = switchEnabled)
        }
    }
}

@Composable
private fun EmptyState(enabled: Boolean, onCreate: () -> Unit) {
    val palette = LocalPalette.current
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 30.dp,
        depth = 8.dp,
        contentPadding = PaddingValues(24.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            VIconImage(
                icon = VIcon.Mic,
                tint = palette.accent,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = palette.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
            NeuButton(
                text = stringResource(R.string.home_new_alarm),
                onClick = onCreate,
                icon = VIcon.Plus,
                accent = true,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun StoreIssueBanner(issue: StoreIssue, onDismiss: () -> Unit) {
    val message = when (issue) {
        is StoreIssue.Corrupt -> stringResource(R.string.store_issue_corrupt, issue.backupFileName ?: "-")
        is StoreIssue.SchemaTooNew -> stringResource(
            R.string.store_issue_too_new,
            issue.found,
        )

        is StoreIssue.DroppedEntries -> stringResource(R.string.store_issue_dropped, issue.count)
        is StoreIssue.SaveFailed -> stringResource(R.string.store_issue_save_failed, issue.message)
    }
    NeuBanner(
        text = message,
        actionText = stringResource(R.string.action_close),
        onAction = onDismiss,
    )
}
