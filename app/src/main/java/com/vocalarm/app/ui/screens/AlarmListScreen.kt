package com.vocalarm.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.vocalarm.app.ui.Eyebrow
import com.vocalarm.app.ui.MetaItem
import com.vocalarm.app.ui.NeuBanner
import com.vocalarm.app.ui.StatusPill
import com.vocalarm.app.ui.TabBarSpacer
import com.vocalarm.app.ui.VocalarmMark
import com.vocalarm.app.ui.contentSummary
import com.vocalarm.app.ui.dayShortText
import com.vocalarm.app.ui.loopModeText
import com.vocalarm.app.ui.neu.NeuButton
import com.vocalarm.app.ui.neu.NeuCard
import com.vocalarm.app.ui.neu.NeuIconButton
import com.vocalarm.app.ui.neu.NeuIconTile
import com.vocalarm.app.ui.neu.NeuSwitch
import com.vocalarm.app.ui.neu.NeuTone
import com.vocalarm.app.ui.neu.VIcon
import com.vocalarm.app.ui.neu.VIconImage
import com.vocalarm.app.ui.remainingText
import com.vocalarm.app.ui.repeatText
import com.vocalarm.app.ui.theme.LocalPalette
import com.vocalarm.app.ui.theme.TimeDisplayStyle
import com.vocalarm.app.ui.vm.AlarmListViewModel
import java.time.Instant
import java.time.ZoneId

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

    var notificationsEnabled by rememberSaveable { mutableStateOf(true) }
    var exactAlarmsEnabled by rememberSaveable { mutableStateOf(true) }
    var fullScreenAllowed by rememberSaveable { mutableStateOf(true) }
    var requestedNotifications by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            notificationsEnabled = NotificationChannels.areNotificationsEnabled(context) && granted
        },
    )

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsEnabled = NotificationChannels.areNotificationsEnabled(context)
        exactAlarmsEnabled = container.scheduler.canScheduleExact()
        fullScreenAllowed = NotificationChannels.canUseFullScreenIntent(context)
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HomeHeader(now = state.now, onOpenAppearance = onOpenAppearance) }

            if (state.nextAlarm != null && state.nextTriggerAt != null) {
                val next = state.nextAlarm!!
                item {
                    NextAlarmHero(
                        alarm = next,
                        triggerAt = state.nextTriggerAt!!,
                        now = state.now,
                        onClick = { onEdit(next.id) },
                    )
                }
            } else if (state.alarms.isNotEmpty()) {
                item { QuietDayCard(onCreate = { onEdit(null) }) }
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !fullScreenAllowed) {
                    item {
                        NeuBanner(
                            text = stringResource(R.string.home_full_screen_off),
                            actionText = stringResource(R.string.home_open_settings),
                            onAction = { openFullScreenIntentSettings(context) },
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
                item { StoreIssueBanner(issue = issue, onDismiss = viewModel::clearIssue) }
            }

            if (state.alarms.isEmpty()) {
                item { EmptyState(onCreate = { onEdit(null) }) }
                item { TipsCard() }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.home_section_alarms),
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.textPrimary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = stringResource(
                                R.string.home_alarm_count,
                                state.alarms.count { it.enabled },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                        )
                    }
                }
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
                    .padding(end = 24.dp, bottom = 22.dp),
                size = 62.dp,
                accent = true,
            )
        }
    }
}

/** Sends the user to the system page where full-screen intents are granted; falls back to the
 *  notification settings page on builds or ROMs that do not expose it. */
private fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    runCatching {
        context.startActivity(NotificationChannels.fullScreenIntentSettingsAction(context))
    }.recoverCatching {
        context.startActivity(NotificationChannels.notificationSettingsAction(context))
    }
}

@Composable
private fun HomeHeader(now: Long, onOpenAppearance: () -> Unit) {
    val palette = LocalPalette.current
    val zoned = remember(now) { Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()) }
    val greetingRes = when (zoned.hour) {
        in 5..11 -> R.string.home_greeting_morning
        in 12..17 -> R.string.home_greeting_afternoon
        else -> R.string.home_greeting_evening
    }
    val weekday = dayShortText(zoned.dayOfWeek)
    val dateLine = stringResource(R.string.home_date_line, zoned.monthValue, zoned.dayOfMonth, weekday)

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Eyebrow(text = "VOCALARM", color = palette.accent)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(greetingRes),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                color = palette.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = dateLine,
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )
        }
        NeuIconButton(
            icon = VIcon.Droplet,
            contentDescription = stringResource(R.string.tab_appearance),
            onClick = onOpenAppearance,
            size = 48.dp,
            tint = palette.accent,
        )
    }
}

@Composable
private fun NextAlarmHero(alarm: Alarm, triggerAt: Long, now: Long, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val remaining = remainingText(triggerAt = triggerAt, now = now)
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 32.dp,
        depth = 12.dp,
        contentPadding = PaddingValues(0.dp),
        onClick = onClick,
        glow = palette.accent.copy(alpha = 0.42f),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = 1.dp.toPx()
                drawCircle(
                    color = palette.accent.copy(alpha = 0.10f),
                    radius = size.width * 0.46f,
                    center = Offset(size.width * 0.96f, -size.height * 0.10f),
                    style = Stroke(width = stroke),
                )
                drawCircle(
                    color = palette.accent.copy(alpha = 0.07f),
                    radius = size.width * 0.34f,
                    center = Offset(size.width * 0.96f, -size.height * 0.10f),
                    style = Stroke(width = stroke),
                )
                drawCircle(
                    color = palette.accent.copy(alpha = 0.08f),
                    radius = size.width * 0.15f,
                    center = Offset(size.width * 0.04f, size.height * 0.96f),
                )
            }
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        icon = VIcon.Clock,
                        text = stringResource(R.string.home_next_alarm),
                    )
                    Spacer(Modifier.weight(1f))
                    if (remaining != null) {
                        Text(
                            text = remaining,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.accent,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    AlarmTimeText(alarm = alarm, fontSize = 62.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.padding(bottom = 9.dp)) {
                        Text(
                            text = alarm.label.ifBlank { stringResource(R.string.alarm_default_label) },
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = repeatText(alarm.repeatDays) + " · " + contentSummary(alarm),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetaItem(icon = VIcon.Loop, text = loopModeText(alarm.loopMode, alarm.loopCount))
                    Spacer(Modifier.width(16.dp))
                    MetaItem(
                        icon = if (alarm.hasRecording) VIcon.Mic else VIcon.Volume,
                        text = contentSummary(alarm),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.home_hero_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accent,
                    )
                    Spacer(Modifier.width(2.dp))
                    VIconImage(
                        icon = VIcon.ChevronRight,
                        tint = palette.accent,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuietDayCard(onCreate: () -> Unit) {
    val palette = LocalPalette.current
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 28.dp,
        depth = 8.dp,
        contentPadding = PaddingValues(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeuIconTile(icon = VIcon.Snooze, size = 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_quiet_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = stringResource(R.string.home_quiet_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            NeuButton(
                text = stringResource(R.string.home_new_alarm),
                onClick = onCreate,
                icon = VIcon.Plus,
                modifier = Modifier.fillMaxWidth(),
                accent = true,
                height = 48.dp,
            )
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
        depth = if (alarm.enabled) 7.dp else 3.dp,
        tone = if (alarm.enabled) NeuTone.Raised else NeuTone.Inset,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (alarm.enabled) palette.accent else palette.muted.copy(alpha = 0.5f)),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = alarm.timeText,
                        style = TimeDisplayStyle.copy(fontSize = 38.sp, lineHeight = 43.sp),
                        color = if (alarm.enabled) palette.textPrimary else palette.muted,
                    )
                    if (!alarm.enabled) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.home_alarm_off),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = alarm.label.ifBlank { stringResource(R.string.alarm_default_label) },
                    modifier = Modifier.padding(start = 15.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (alarm.enabled) palette.textSecondary else palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(9.dp))
                Row(
                    modifier = Modifier.padding(start = 15.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetaItem(icon = VIcon.Loop, text = repeatText(alarm.repeatDays))
                    MetaItem(
                        icon = if (alarm.hasRecording) VIcon.Mic else VIcon.Volume,
                        text = contentSummary(alarm),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            NeuSwitch(checked = alarm.enabled, onCheckedChange = onToggle, enabled = switchEnabled)
        }
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit) {
    val palette = LocalPalette.current
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 32.dp,
        depth = 10.dp,
        contentPadding = PaddingValues(26.dp),
        glow = palette.accent.copy(alpha = 0.25f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            VocalarmMark(size = 104.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.home_empty_features),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            NeuButton(
                text = stringResource(R.string.home_empty_action),
                onClick = onCreate,
                icon = VIcon.Plus,
                accent = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TipsCard() {
    val palette = LocalPalette.current
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 26.dp,
        depth = 6.dp,
        contentPadding = PaddingValues(20.dp),
    ) {
        Column {
            Eyebrow(text = stringResource(R.string.home_tips_title), color = palette.muted)
            Spacer(Modifier.height(14.dp))
            TipRow(
                icon = VIcon.Mic,
                title = stringResource(R.string.home_tip_record_title),
                body = stringResource(R.string.home_tip_record_body),
            )
            Spacer(Modifier.height(14.dp))
            TipRow(
                icon = VIcon.Volume,
                title = stringResource(R.string.home_tip_speech_title),
                body = stringResource(R.string.home_tip_speech_body),
            )
            Spacer(Modifier.height(14.dp))
            TipRow(
                icon = VIcon.Bell,
                title = stringResource(R.string.home_tip_lock_title),
                body = stringResource(R.string.home_tip_lock_body),
            )
        }
    }
}

@Composable
private fun TipRow(icon: VIcon, title: String, body: String) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        NeuIconTile(icon = icon, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )
        }
    }
}

@Composable
private fun StoreIssueBanner(issue: StoreIssue, onDismiss: () -> Unit) {
    val message = when (issue) {
        is StoreIssue.Corrupt -> stringResource(R.string.store_issue_corrupt, issue.backupFileName ?: "-")
        is StoreIssue.SchemaTooNew -> stringResource(R.string.store_issue_too_new, issue.found)
        is StoreIssue.DroppedEntries -> stringResource(R.string.store_issue_dropped, issue.count)
        is StoreIssue.SaveFailed -> stringResource(R.string.store_issue_save_failed, issue.message)
    }
    NeuBanner(
        text = message,
        actionText = stringResource(R.string.action_close),
        onAction = onDismiss,
    )
}
