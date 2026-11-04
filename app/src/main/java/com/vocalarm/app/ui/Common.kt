package com.vocalarm.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vocalarm.app.R
import com.vocalarm.app.domain.Alarm
import com.vocalarm.app.domain.LoopMode
import com.vocalarm.app.domain.Remaining
import com.vocalarm.app.domain.RepeatKind
import com.vocalarm.app.domain.RepeatPlan
import com.vocalarm.app.domain.VoicePreset
import com.vocalarm.app.ui.neu.NeuButton
import com.vocalarm.app.ui.neu.NeuCard
import com.vocalarm.app.ui.neu.NeuTone
import com.vocalarm.app.ui.neu.VIcon
import com.vocalarm.app.ui.neu.VIconImage
import com.vocalarm.app.ui.theme.LocalPalette
import java.time.DayOfWeek
import java.util.Locale

@Composable
fun AlarmTimeText(alarm: Alarm, modifier: Modifier = Modifier, fontSize: TextUnit = 42.sp) {
    val palette = LocalPalette.current
    Text(
        text = alarm.timeText,
        modifier = modifier,
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = fontSize,
            lineHeight = fontSize * 1.08f,
        ),
        color = if (alarm.enabled) palette.textPrimary else palette.muted,
    )
}

@Composable
fun dayShortText(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> stringResource(R.string.editor_day_mon)
    DayOfWeek.TUESDAY -> stringResource(R.string.editor_day_tue)
    DayOfWeek.WEDNESDAY -> stringResource(R.string.editor_day_wed)
    DayOfWeek.THURSDAY -> stringResource(R.string.editor_day_thu)
    DayOfWeek.FRIDAY -> stringResource(R.string.editor_day_fri)
    DayOfWeek.SATURDAY -> stringResource(R.string.editor_day_sat)
    DayOfWeek.SUNDAY -> stringResource(R.string.editor_day_sun)
}

@Composable
fun repeatText(days: Set<DayOfWeek>): String = when (RepeatPlan.kind(days)) {
    RepeatKind.Once -> stringResource(R.string.home_once)
    RepeatKind.Daily -> stringResource(R.string.home_daily)
    RepeatKind.Weekdays -> stringResource(R.string.home_weekdays)
    RepeatKind.Weekends -> stringResource(R.string.home_weekends)
    RepeatKind.Custom -> {
        val labels = ArrayList<String>(days.size)
        for (day in RepeatPlan.ordered(days)) {
            labels += dayShortText(day)
        }
        labels.joinToString(" ")
    }
}

@Composable
fun remainingText(triggerAt: Long?, now: Long): String? {
    if (triggerAt == null || triggerAt <= now) return null
    val remaining = Remaining.of(triggerAt, now)
    return when {
        remaining.hours > 0 -> stringResource(
            R.string.home_remaining_hours_minutes,
            remaining.hours,
            remaining.minutes,
        )

        remaining.minutes > 0 -> stringResource(R.string.home_remaining_minutes, remaining.minutes)
        else -> stringResource(R.string.home_remaining_soon)
    }
}

@Composable
fun contentSummary(alarm: Alarm): String {
    val parts = buildList {
        if (alarm.hasRecording) add(stringResource(R.string.home_content_recording))
        if (alarm.hasText) add(stringResource(R.string.home_content_speech))
    }
    return parts.joinToString(" + ")
}

fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

fun formatRate(rate: Float): String = String.format(Locale.ROOT, "%.1f", rate)

@Composable
fun NeuProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    val palette = LocalPalette.current
    Canvas(modifier.fillMaxWidth().height(height)) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = palette.track, cornerRadius = radius, size = size)
        val filled = size.width * progress.coerceIn(0f, 1f)
        if (filled > 0f) {
            drawRoundRect(
                color = palette.accent,
                size = Size(filled, size.height),
                cornerRadius = radius,
            )
        }
        drawRect(
            color = palette.darkShadow.copy(alpha = palette.darkShadowAlpha * 0.18f),
            topLeft = Offset.Zero,
            size = Size(size.width, size.height / 2f),
        )
    }
}

@Composable
fun NeuDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = stringResource(R.string.action_cancel),
) {
    val palette = LocalPalette.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NeuCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            radius = 28.dp,
            depth = 10.dp,
            contentPadding = PaddingValues(24.dp),
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NeuButton(
                        text = dismissText,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        height = 46.dp,
                    )
                    NeuButton(
                        text = confirmText,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        height = 46.dp,
                        accent = true,
                    )
                }
            }
        }
    }
}

@Composable
fun NeuBanner(
    text: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    NeuCard(
        modifier = modifier.fillMaxWidth(),
        radius = 20.dp,
        depth = 4.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VIconImage(
                icon = VIcon.Bell,
                tint = palette.accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelLarge,
                color = palette.accent,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
fun NeuTabBar(
    tabs: List<Tab>,
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    NeuCard(
        modifier = modifier,
        radius = 32.dp,
        depth = 8.dp,
        contentPadding = PaddingValues(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEach { tab ->
                val active = tab == selected
                NeuCard(
                    modifier = Modifier.weight(1f),
                    radius = 24.dp,
                    depth = if (active) 2.dp else 6.dp,
                    tone = if (active) NeuTone.Inset else NeuTone.Raised,
                    fill = if (active) palette.background else palette.background,
                    contentPadding = PaddingValues(vertical = 10.dp),
                    onClick = { onSelect(tab) },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        VIconImage(
                            icon = tab.icon,
                            tint = if (active) palette.accent else palette.muted,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) palette.accent else palette.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

enum class Tab(val labelRes: Int, val icon: VIcon) {
    Alarms(R.string.tab_alarms, VIcon.Bell),
    Appearance(R.string.tab_appearance, VIcon.Droplet),
    About(R.string.tab_about, VIcon.Info),
}

@Composable
fun SectionCardTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalPalette.current.muted,
    )
}

@Composable
fun SoftColorSwatch(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(14.dp)) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            drawCircle(color = color)
        }
    }
}

@Composable
fun TabBarSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier.height(124.dp))
}
