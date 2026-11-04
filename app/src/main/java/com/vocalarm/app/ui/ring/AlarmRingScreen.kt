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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocalarm.app.AppContainer
import com.vocalarm.app.R
import com.vocalarm.app.alarm.AlarmPlaybackEngine
import com.vocalarm.app.alarm.AlarmService
import com.vocalarm.app.ui.repeatText
import com.vocalarm.app.ui.neu.NeuButton
import com.vocalarm.app.ui.neu.NeuCard
import com.vocalarm.app.ui.neu.VIcon
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
        snapshotFlow { container.ringing.current.value }.collect { current ->
            if (current == null) onFinish()
        }
    }

    val current = ringing
    if (current == null) {
        // The service sets the state before posting the notification; this covers the short race.
        Box(Modifier.fillMaxSize().background(palette.background))
        return
    }
    val alarm = current.alarm

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Box(contentAlignment = Alignment.Center) {
                PulseRings()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = alarm.timeText,
                        style = TimeDisplayStyle.copy(fontSize = 76.sp, lineHeight = 84.sp),
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = alarm.label.ifBlank { stringResource(R.string.ring_label_fallback) },
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = repeatText(alarm.repeatDays),
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
            )
            Spacer(Modifier.height(10.dp))
            val playingLabel = when (nowPlaying?.segment) {
                AlarmPlaybackEngine.Segment.Recording -> stringResource(R.string.ring_now_playing_recording)
                AlarmPlaybackEngine.Segment.Speech -> stringResource(R.string.ring_now_playing_speech)
                null -> null
            }
            if (playingLabel != null) {
                Text(
                    text = playingLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.accent,
                )
            }

            current.message?.let { message ->
                Spacer(Modifier.height(20.dp))
                NeuCard(
                    radius = 20.dp,
                    depth = 5.dp,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.danger,
                        textAlign = TextAlign.Center,
                    )
                }
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
                    NeuButton(
                        text = stringResource(R.string.action_snooze, minutes),
                        onClick = { AlarmService.snooze(context, minutes, alarm.id) },
                        modifier = Modifier.weight(1f),
                        icon = VIcon.Snooze,
                        height = 52.dp,
                    )
                }
                if (alarm.snoozeMinutes.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            NeuButton(
                text = stringResource(R.string.action_stop),
                onClick = { AlarmService.stop(context) },
                modifier = Modifier.fillMaxWidth(),
                icon = VIcon.Stop,
                accent = true,
                height = 58.dp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.ring_stop_hint),
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
            animation = tween(durationMillis = 2_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringPulseValue",
    )
    Canvas(Modifier.size(300.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val minRadius = size.minDimension * 0.28f
        val maxRadius = size.minDimension * 0.5f
        for (index in 0..2) {
            val phase = (pulse + index * 0.33f) % 1f
            val radius = minRadius + (maxRadius - minRadius) * phase
            drawCircle(
                color = palette.accent.copy(alpha = (1f - phase) * 0.22f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}

private const val STARTUP_GRACE_MS = 5_000L
