package com.vocalarm.app.ui.ring

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocalarm.app.AppContainer
import com.vocalarm.app.R
import com.vocalarm.app.alarm.AlarmPlaybackEngine
import com.vocalarm.app.alarm.AlarmService
import com.vocalarm.app.ui.Eyebrow
import com.vocalarm.app.ui.StatusPill
import com.vocalarm.app.ui.neu.NeuButton
import com.vocalarm.app.ui.neu.NeuCard
import com.vocalarm.app.ui.neu.VIcon
import com.vocalarm.app.ui.neu.VIconImage
import com.vocalarm.app.ui.repeatText
import com.vocalarm.app.ui.theme.LocalPalette
import com.vocalarm.app.ui.theme.TimeDisplayStyle
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun AlarmRingScreen(container: AppContainer, onFinish: () -> Unit) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val ringing by container.ringing.current.collectAsStateWithLifecycle()
    val nowPlaying by container.playback.nowPlaying.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        val appeared = withTimeoutOrNull(STARTUP_GRACE_MS) {
            container.ringing.current.filterNotNull().first()
        }
        if (appeared == null) {
            onFinish()
            return@LaunchedEffect
        }
        // Collect the StateFlow itself. `snapshotFlow { flow.value }` never re-emits, because a
        // StateFlow is not a Compose snapshot state, which left the screen stuck as a blank page
        // after the alarm was dismissed from the notification.
        container.ringing.current.collect { current ->
            if (current == null) onFinish()
        }
    }

    val current = ringing
    if (current == null) {
        Box(Modifier.fillMaxSize().background(palette.background))
        return
    }
    val alarm = current.alarm

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = if (palette.isDark) 0.26f else 0.20f),
                            palette.background,
                        ),
                        center = Offset(size.width / 2f, size.height * 0.30f),
                        radius = size.height * 0.62f,
                    ),
                )
                drawCircle(
                    color = palette.lightShadow.copy(alpha = if (palette.isDark) 0.04f else 0.14f),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 0.1f, size.height * 0.95f),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Eyebrow(text = "VOCALARM", color = palette.accent)
            Spacer(Modifier.height(8.dp))
            Text(
                text = alarm.label.ifBlank { stringResource(R.string.ring_label_fallback) },
                style = MaterialTheme.typography.titleLarge,
                color = palette.textSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            Box(contentAlignment = Alignment.Center) {
                PulseRings()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = alarm.timeText,
                        style = TimeDisplayStyle.copy(fontSize = 82.sp, lineHeight = 88.sp),
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(
                            icon = VIcon.Loop,
                            text = repeatText(alarm.repeatDays),
                        )
                        Spacer(Modifier.width(8.dp))
                        val playingLabel = when (nowPlaying?.segment) {
                            AlarmPlaybackEngine.Segment.Recording -> stringResource(R.string.ring_now_playing_recording)
                            AlarmPlaybackEngine.Segment.Speech -> stringResource(R.string.ring_now_playing_speech)
                            null -> null
                        }
                        if (playingLabel != null) {
                            StatusPill(icon = VIcon.Volume, text = playingLabel)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = alarm.text?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.ring_now_playing_recording),
                        modifier = Modifier.padding(horizontal = 34.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            current.message?.let { message ->
                NeuCard(
                    radius = 20.dp,
                    depth = 6.dp,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.danger,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = stringResource(R.string.ring_snooze_hint),
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                alarm.snoozeMinutes.take(2).forEach { minutes ->
                    SnoozeCard(
                        minutes = minutes,
                        modifier = Modifier.weight(1f),
                        onClick = { AlarmService.snooze(context, minutes, alarm.id) },
                    )
                }
                if (alarm.snoozeMinutes.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(14.dp))
            NeuButton(
                text = stringResource(R.string.ring_stop_label),
                onClick = { AlarmService.stop(context) },
                modifier = Modifier.fillMaxWidth(),
                icon = VIcon.Stop,
                accent = true,
                height = 60.dp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ring_stop_hint),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )
        }
    }
}

@Composable
private fun SnoozeCard(minutes: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    NeuCard(
        modifier = modifier,
        radius = 24.dp,
        depth = 8.dp,
        contentPadding = PaddingValues(vertical = 14.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.editor_minutes_short, minutes),
                style = MaterialTheme.typography.titleLarge,
                color = palette.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.ring_snooze_caption),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )
        }
    }
}

@Composable
private fun PulseRings() {
    val palette = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "ringPulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringPulseValue",
    )
    Canvas(Modifier.size(310.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val minRadius = size.minDimension * 0.26f
        val maxRadius = size.minDimension * 0.50f
        for (index in 0..2) {
            val phase = (pulse + index * 0.33f) % 1f
            val radius = minRadius + (maxRadius - minRadius) * phase
            drawCircle(
                color = palette.accent.copy(alpha = (1f - phase) * 0.20f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.4.dp.toPx()),
            )
        }
        drawCircle(
            color = palette.accent.copy(alpha = 0.06f),
            radius = size.minDimension * 0.30f,
            center = center,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.05f),
            radius = size.minDimension * 0.22f,
            center = center,
        )
    }
}

private const val STARTUP_GRACE_MS = 5_000L
