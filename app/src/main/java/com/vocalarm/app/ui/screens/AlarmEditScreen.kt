package com.vocalarm.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vocalarm.app.AppContainer
import com.vocalarm.app.R
import com.vocalarm.app.audio.VoiceRecorder
import com.vocalarm.app.domain.Alarm
import com.vocalarm.app.domain.LoopMode
import com.vocalarm.app.domain.RepeatKind
import com.vocalarm.app.domain.RepeatPlan
import com.vocalarm.app.domain.VoiceCatalog
import com.vocalarm.app.domain.VoiceDescriptor
import com.vocalarm.app.domain.VoiceGender
import com.vocalarm.app.domain.VoicePreset
import com.vocalarm.app.ui.Eyebrow
import com.vocalarm.app.ui.MetaItem
import com.vocalarm.app.ui.NeuDialog
import com.vocalarm.app.ui.NeuProgressBar
import com.vocalarm.app.ui.StatusPill
import com.vocalarm.app.ui.formatDuration
import com.vocalarm.app.ui.formatRate
import com.vocalarm.app.ui.neu.NeuButton
import com.vocalarm.app.ui.neu.NeuCard
import com.vocalarm.app.ui.neu.NeuChip
import com.vocalarm.app.ui.neu.NeuDivider
import com.vocalarm.app.ui.neu.NeuIconButton
import com.vocalarm.app.ui.neu.NeuIconTile
import com.vocalarm.app.ui.neu.NeuSlider
import com.vocalarm.app.ui.neu.NeuSwitch
import com.vocalarm.app.ui.neu.NeuTextField
import com.vocalarm.app.ui.neu.TimeWheel
import com.vocalarm.app.ui.neu.VIcon
import com.vocalarm.app.ui.neu.VIconImage
import com.vocalarm.app.ui.neu.neuSurface
import com.vocalarm.app.ui.repeatText
import com.vocalarm.app.ui.theme.LocalPalette
import com.vocalarm.app.ui.theme.TimeDisplayStyle
import com.vocalarm.app.ui.vm.AlarmDraft
import com.vocalarm.app.ui.vm.AlarmEditViewModel
import com.vocalarm.app.ui.vm.rememberEditorViewModelStoreOwner
import java.time.DayOfWeek
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun AlarmEditScreen(
    container: AppContainer,
    alarmId: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val editorStore = rememberEditorViewModelStoreOwner()
    val viewModel: AlarmEditViewModel = viewModel(
        viewModelStoreOwner = editorStore,
        initializer = {
            AlarmEditViewModel(
                alarmId = alarmId,
                repository = container.alarmRepository,
                scheduler = container.scheduler,
                recordings = container.recordings,
                recorder = container.recorder,
                player = container.player,
                tts = container.tts,
            )
        },
    )

    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val previewingSpeech by viewModel.previewingSpeech.collectAsStateWithLifecycle()
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    val baseline = remember { viewModel.draft.value }
    val recording = recordingState as? VoiceRecorder.RecordState.Recording
    val hasChanges = draft != baseline || recording != null

    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showVoicePicker by remember { mutableStateOf(false) }

    fun performClose() {
        viewModel.cancelRecording()
        viewModel.stopPreviews()
        onClose()
    }

    fun requestClose() {
        if (hasChanges) showDiscard = true else performClose()
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                viewModel.startRecording()
            } else {
                viewModel.notifyMicPermissionDenied()
            }
        },
    )

    fun startRecording() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startRecording() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(saved) {
        if (saved) onClose()
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(3_200)
            viewModel.consumeMessage()
        }
    }
    BackHandler { requestClose() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            EditorTopBar(
                isNew = viewModel.isNewAlarm,
                timeText = String.format(Locale.ROOT, "%02d:%02d", draft.hour, draft.minute),
                onClose = ::requestClose,
                onSave = {
                    if (recording != null) viewModel.cancelRecording()
                    viewModel.stopPreviews()
                    viewModel.save()
                },
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 44.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    TimeCard(
                        draft = draft,
                        onTimeChange = viewModel::setTime,
                        onLabelChange = viewModel::setLabel,
                    )
                }
                item {
                    RepeatCard(
                        draft = draft,
                        onPreset = viewModel::setRepeatPreset,
                        onToggleDay = viewModel::toggleDay,
                    )
                }
                item {
                    RecordingCard(
                        draft = draft,
                        recording = recording,
                        preview = preview,
                        onStartRecording = ::startRecording,
                        onStopRecording = viewModel::stopRecording,
                        onDeleteRecording = viewModel::deleteRecording,
                        onTogglePreview = viewModel::toggleRecordingPreview,
                    )
                }
                item {
                    SpeechCard(
                        draft = draft,
                        previewingSpeech = previewingSpeech,
                        onTextChange = viewModel::setText,
                        onTogglePreview = viewModel::toggleSpeechPreview,
                    )
                }
                item {
                    VoiceCard(
                        draft = draft,
                        voices = voices,
                        onPreset = viewModel::setVoicePreset,
                        onOpenPicker = { showVoicePicker = true },
                        onRateChange = viewModel::setSpeechRate,
                    )
                }
                item {
                    PlaybackCard(
                        draft = draft,
                        onLoopMode = viewModel::setLoopMode,
                        onLoopCount = viewModel::setLoopCount,
                        onVolume = viewModel::setVolume,
                        onFadeIn = viewModel::setFadeIn,
                        onFadeSeconds = viewModel::setFadeSeconds,
                    )
                }
                item { SnoozeCard(draft = draft) }
                if (!viewModel.isNewAlarm) {
                    item {
                        NeuButton(
                            text = stringResource(R.string.editor_delete),
                            onClick = { showDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                            icon = VIcon.Trash,
                            contentTint = palette.danger,
                            height = 50.dp,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
        ) {
            NeuCard(
                radius = 20.dp,
                depth = 10.dp,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Text(
                    text = editorMessageText(message.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary,
                )
            }
        }
    }

    if (showDiscard) {
        NeuDialog(
            title = stringResource(R.string.editor_discard_title),
            body = stringResource(R.string.editor_discard_body),
            confirmText = stringResource(R.string.editor_discard_confirm),
            onConfirm = {
                showDiscard = false
                performClose()
            },
            onDismiss = { showDiscard = false },
        )
    }
    if (showDelete) {
        NeuDialog(
            title = stringResource(R.string.home_delete_title),
            body = stringResource(R.string.home_delete_body),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = {
                showDelete = false
                viewModel.delete()
            },
            onDismiss = { showDelete = false },
        )
    }
    if (showVoicePicker) {
        VoicePickerDialog(
            voices = voices,
            selectedName = draft.voiceName,
            onSelect = { voice ->
                viewModel.setCustomVoice(voice.name)
                showVoicePicker = false
            },
            onDefault = {
                viewModel.setVoicePreset(VoicePreset.Default)
                showVoicePicker = false
            },
            onDismiss = { showVoicePicker = false },
        )
    }
}

@Composable
private fun EditorTopBar(
    isNew: Boolean,
    timeText: String,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeuIconButton(
            icon = VIcon.Close,
            contentDescription = stringResource(R.string.action_cancel),
            onClick = onClose,
            size = 42.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Eyebrow(
                text = stringResource(
                    if (isNew) R.string.editor_eyebrow_new else R.string.editor_eyebrow_edit,
                ),
                color = palette.accent,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (isNew) stringResource(R.string.editor_header_new) else timeText,
                style = MaterialTheme.typography.titleLarge,
                color = palette.textPrimary,
            )
        }
        NeuButton(
            text = stringResource(R.string.action_save),
            onClick = onSave,
            accent = true,
            height = 44.dp,
        )
    }
}

@Composable
private fun TimeCard(draft: AlarmDraft, onTimeChange: (Int, Int) -> Unit, onLabelChange: (String) -> Unit) {
    val palette = LocalPalette.current
    NeuCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, depth = 10.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.editor_time),
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = repeatText(draft.repeatDays),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.accent,
                )
            }
            Spacer(Modifier.height(6.dp))
            TimeWheel(
                hour = draft.hour,
                minute = draft.minute,
                onHourChange = { hour -> onTimeChange(hour, draft.minute) },
                onMinuteChange = { minute -> onTimeChange(draft.hour, minute) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            NeuTextField(
                value = draft.label,
                onValueChange = onLabelChange,
                placeholder = stringResource(R.string.editor_label_hint),
                singleLine = true,
                minLines = 1,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RepeatCard(
    draft: AlarmDraft,
    onPreset: (RepeatKind) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
) {
    val palette = LocalPalette.current
    val kind = RepeatPlan.kind(draft.repeatDays)
    NeuCard(modifier = Modifier.fillMaxWidth(), radius = 26.dp, depth = 7.dp) {
        Column {
            EditorSectionHeader(
                icon = VIcon.Refresh,
                title = stringResource(R.string.editor_repeat),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepeatPresetChip(R.string.editor_repeat_once, kind == RepeatKind.Once, { onPreset(RepeatKind.Once) }, Modifier.weight(1f))
                RepeatPresetChip(R.string.editor_repeat_daily, kind == RepeatKind.Daily, { onPreset(RepeatKind.Daily) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepeatPresetChip(
                    R.string.editor_repeat_weekdays,
                    kind == RepeatKind.Weekdays,
                    { onPreset(RepeatKind.Weekdays) },
                    Modifier.weight(1f),
                )
                RepeatPresetChip(
                    R.string.editor_repeat_weekends,
                    kind == RepeatKind.Weekends,
                    { onPreset(RepeatKind.Weekends) },
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                DayOfWeek.values().forEach { day ->
                    DayToggle(
                        day = day,
                        selected = day in draft.repeatDays,
                        onClick = { onToggleDay(day) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RepeatPresetChip(labelRes: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    NeuChip(
        label = stringResource(labelRes),
        selected = selected,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun DayToggle(day: DayOfWeek, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .neuSurface(
                palette = palette,
                fill = if (selected) palette.accent else palette.surface,
                radius = 14.dp,
                depth = if (selected) 3.dp else 4.dp,
                blur = 12.dp,
                glowColor = if (selected) palette.accent.copy(alpha = 0.35f) else null,
            )
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(palette.accentGradient, shape)
                } else {
                    Modifier.border(1.dp, palette.hairline, shape)
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = com.vocalarm.app.ui.dayShortText(day),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) palette.onAccent else palette.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RecordingCard(
    draft: AlarmDraft,
    recording: VoiceRecorder.RecordState.Recording?,
    preview: com.vocalarm.app.audio.PreviewState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDeleteRecording: () -> Unit,
    onTogglePreview: () -> Unit,
) {
    val palette = LocalPalette.current
    NeuCard(modifier = Modifier.fillMaxWidth(), radius = 26.dp, depth = 7.dp) {
        Column {
            EditorSectionHeader(
                icon = VIcon.Mic,
                title = stringResource(R.string.editor_recording),
                subtitle = when {
                    recording != null -> null
                    draft.recordingFile != null -> stringResource(R.string.editor_recording_saved)
                    else -> stringResource(R.string.editor_recording_status_empty)
                },
            )
            Spacer(Modifier.height(14.dp))

            when {
                recording != null -> {
                    AmplitudeBars(
                        amplitude = recording.amplitude,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatDuration(recording.elapsedMs),
                            style = TimeDisplayStyle.copy(fontSize = 24.sp, lineHeight = 28.sp),
                            color = palette.textPrimary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.editor_recording_tip),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    NeuButton(
                        text = stringResource(R.string.editor_record_stop),
                        onClick = onStopRecording,
                        modifier = Modifier.fillMaxWidth(),
                        icon = VIcon.Stop,
                        accent = true,
                    )
                }

                draft.recordingFile != null -> {
                    val isCurrent = preview.file == draft.recordingFile
                    val playing = isCurrent && preview.isPlaying
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeuIconButton(
                            icon = if (playing) VIcon.Pause else VIcon.Play,
                            contentDescription = stringResource(
                                if (playing) R.string.editor_record_pause else R.string.editor_record_play,
                            ),
                            onClick = onTogglePreview,
                            size = 48.dp,
                            accent = true,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            NeuProgressBar(progress = if (isCurrent) preview.progress else 0f)
                            Spacer(Modifier.height(7.dp))
                            Text(
                                text = if (isCurrent && preview.durationMs > 0L) {
                                    "${formatDuration(preview.positionMs)} / ${formatDuration(preview.durationMs)}"
                                } else {
                                    stringResource(R.string.editor_recording_saved)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.muted,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NeuButton(
                            text = stringResource(R.string.editor_record_redo),
                            onClick = onStartRecording,
                            modifier = Modifier.weight(1f),
                            icon = VIcon.Mic,
                            height = 44.dp,
                        )
                        NeuButton(
                            text = stringResource(R.string.editor_record_delete),
                            onClick = onDeleteRecording,
                            modifier = Modifier.weight(1f),
                            icon = VIcon.Trash,
                            contentTint = palette.danger,
                            height = 44.dp,
                        )
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(104.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(palette.background)
                            .border(1.dp, palette.hairline, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NeuIconTile(icon = VIcon.Mic, size = 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.editor_recording_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary,
                                )
                                Spacer(Modifier.height(8.dp))
                                NeuButton(
                                    text = stringResource(R.string.editor_record_start),
                                    onClick = onStartRecording,
                                    icon = VIcon.Record,
                                    accent = true,
                                    height = 40.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeechCard(
    draft: AlarmDraft,
    previewingSpeech: Boolean,
    onTextChange: (String) -> Unit,
    onTogglePreview: () -> Unit,
) {
    val palette = LocalPalette.current
    NeuCard(modifier = Modifier.fillMaxWidth(), radius = 26.dp, depth = 7.dp) {
        Column {
            EditorSectionHeader(
                icon = VIcon.Volume,
                title = stringResource(R.string.editor_speech),
                subtitle = null,
                trailing = {
                    NeuButton(
                        text = stringResource(
                            if (previewingSpeech) R.string.editor_speech_stop else R.string.editor_speech_preview,
                        ),
                        onClick = onTogglePreview,
                        icon = if (previewingSpeech) VIcon.Stop else VIcon.Play,
                        height = 40.dp,
                    )
                },
            )
            Spacer(Modifier.height(14.dp))
            NeuTextField(
                value = draft.text,
                onValueChange = onTextChange,
                placeholder = stringResource(R.string.editor_speech_placeholder),
                minLines = 3,
                maxLines = 6,
                singleLine = false,
            )
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (previewingSpeech) {
                        stringResource(R.string.editor_speech_previewing)
                    } else {
                        stringResource(R.string.editor_speech_hint)
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (previewingSpeech) palette.accent else palette.muted,
                )
                Text(
                    text = stringResource(R.string.editor_character_count, draft.text.length),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
            }
        }
    }
}

@Composable
private fun VoiceCard(
    draft: AlarmDraft,
    voices: List<VoiceDescriptor>,
    onPreset: (VoicePreset) -> Unit,
    onOpenPicker: () -> Unit,
    onRateChange: (Float) -> Unit,
) {
    val palette = LocalPalette.current
    val resolved = remember(draft.voicePreset, draft.voiceName, voices) {
        VoiceCatalog.resolve(draft.voicePreset, draft.voiceName, voices)
    }
    NeuCard(modifier = Modifier.fillMaxWidth(), radius = 26.dp, depth = 7.dp) {
        Column {
            EditorSectionHeader(
                icon = VIcon.Droplet,
                title = stringResource(R.string.editor_voice),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuChip(
                    label = stringResource(R.string.editor_voice_default),
                    selected = draft.voicePreset == VoicePreset.Default,
                    onClick = { onPreset(VoicePreset.Default) },
                    modifier = Modifier.weight(1f),
                )
                NeuChip(
                    label = stringResource(R.string.editor_voice_female),
                    selected = draft.voicePreset == VoicePreset.Female,
                    onClick = { onPreset(VoicePreset.Female) },
                    modifier = Modifier.weight(1f),
                )
                NeuChip(
                    label = stringResource(R.string.editor_voice_male),
                    selected = draft.voicePreset == VoicePreset.Male,
                    onClick = { onPreset(VoicePreset.Male) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surfaceHigh)
                    .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
                    .clickable(enabled = voices.isNotEmpty(), onClick = onOpenPicker)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (draft.voicePreset == VoicePreset.Custom && resolved != null) {
                        stringResource(R.string.editor_voice_more) + " · " + resolved.displayName
                    } else {
                        stringResource(R.string.editor_voice_more)
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (voices.isEmpty()) palette.muted else palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                VIconImage(icon = VIcon.ChevronRight, tint = palette.muted, modifier = Modifier.size(16.dp))
            }
            if (draft.voicePreset != VoicePreset.Default) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (resolved == null) {
                        stringResource(R.string.editor_voice_none)
                    } else {
                        stringResource(R.string.editor_voice_resolved, resolved.displayName)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            NeuDivider()
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.editor_rate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                StatusPill(
                    icon = VIcon.Speed,
                    text = stringResource(R.string.editor_rate_value, formatRate(draft.speechRate)),
                )
            }
            NeuSlider(
                value = draft.speechRate,
                onValueChange = onRateChange,
                valueRange = Alarm.MIN_SPEECH_RATE..Alarm.MAX_SPEECH_RATE,
                label = stringResource(R.string.editor_rate),
            )
        }
    }
}

@Composable
private fun PlaybackCard(
    draft: AlarmDraft,
    onLoopMode: (LoopMode) -> Unit,
    onLoopCount: (Int) -> Unit,
    onVolume: (Float) -> Unit,
    onFadeIn: (Boolean) -> Unit,
    onFadeSeconds: (Int) -> Unit,
) {
    val palette = LocalPalette.current
    NeuCard(modifier = Modifier.fillMaxWidth(), radius = 26.dp, depth = 7.dp) {
        Column {
            EditorSectionHeader(
                icon = VIcon.Play,
                title = stringResource(R.string.editor_loop),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuChip(
                    label = stringResource(R.string.editor_loop_once_short),
                    selected = draft.loopMode == LoopMode.Once,
                    onClick = { onLoopMode(LoopMode.Once) },
                    modifier = Modifier.weight(1f),
                )
                NeuChip(
                    label = stringResource(R.string.editor_loop_times_short),
                    selected = draft.loopMode == LoopMode.Times,
                    onClick = { onLoopMode(LoopMode.Times) },
                    modifier = Modifier.weight(1f),
                )
                NeuChip(
                    label = stringResource(R.string.editor_loop_continuous_short),
                    selected = draft.loopMode == LoopMode.Continuous,
                    onClick = { onLoopMode(LoopMode.Continuous) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (draft.loopMode == LoopMode.Times) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.editor_loop_count_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    NeuIconButton(
                        icon = VIcon.Minus,
                        contentDescription = stringResource(R.string.editor_count_decrease),
                        onClick = { onLoopCount(draft.loopCount - 1) },
                        size = 38.dp,
                        enabled = draft.loopCount > 1,
                    )
                    Text(
                        text = "${draft.loopCount}",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                    )
                    NeuIconButton(
                        icon = VIcon.Plus,
                        contentDescription = stringResource(R.string.editor_count_increase),
                        onClick = { onLoopCount(draft.loopCount + 1) },
                        size = 38.dp,
                        enabled = draft.loopCount < Alarm.MAX_LOOP_COUNT,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            NeuDivider()
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                VIconImage(icon = VIcon.Volume, tint = palette.muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.editor_volume),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.editor_volume_value, (draft.volume * 100).roundToInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.accent,
                )
            }
            NeuSlider(
                value = draft.volume,
                onValueChange = onVolume,
                valueRange = 0.05f..1f,
                label = stringResource(R.string.editor_volume),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.editor_fade),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary,
                )
                if (draft.fadeIn) {
                    Text(
                        text = stringResource(R.string.editor_fade_value, draft.fadeSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                NeuSwitch(checked = draft.fadeIn, onCheckedChange = onFadeIn)
            }
            if (draft.fadeIn) {
                NeuSlider(
                    value = draft.fadeSeconds.toFloat(),
                    onValueChange = { onFadeSeconds(it.roundToInt()) },
                    valueRange = 1f..Alarm.MAX_FADE_SECONDS.toFloat(),
                    label = stringResource(R.string.editor_fade),
                )
            }
        }
    }
}

@Composable
private fun SnoozeCard(draft: AlarmDraft) {
    val palette = LocalPalette.current
    NeuCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, depth = 6.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeuIconTile(icon = VIcon.Snooze, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.editor_snooze),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.editor_snooze_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                draft.snoozeMinutes.forEach { minutes ->
                    OptionPill(text = stringResource(R.string.editor_minutes_short, minutes))
                }
            }
        }
    }
}

@Composable
private fun OptionPill(text: String) {
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
private fun EditorSectionHeader(
    icon: VIcon,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        NeuIconTile(icon = icon, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun AmplitudeBars(amplitude: Float, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val weights = remember { listOf(0.35f, 0.75f, 0.5f, 1f, 0.6f, 0.9f, 0.4f, 0.8f, 0.55f, 0.95f, 0.45f) }
    Canvas(modifier) {
        val barCount = 40
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(1f)
        for (index in 0 until barCount) {
            val weight = weights[index % weights.size]
            val height = size.height * (0.10f + amplitude.coerceIn(0f, 1f) * weight * 0.90f)
            val left = index * (barWidth + gap)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(palette.accent, palette.accentDeep),
                    startY = size.height - height,
                    endY = size.height,
                ),
                topLeft = Offset(left, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
private fun VoicePickerDialog(
    voices: List<VoiceDescriptor>,
    selectedName: String?,
    onSelect: (VoiceDescriptor) -> Unit,
    onDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NeuCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            radius = 30.dp,
            depth = 12.dp,
            contentPadding = PaddingValues(0.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.editor_voice_more),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.textPrimary,
                    )
                    NeuIconButton(
                        icon = VIcon.Close,
                        contentDescription = stringResource(R.string.action_close),
                        onClick = onDismiss,
                        size = 38.dp,
                    )
                }
                VoiceOptionRow(
                    title = stringResource(R.string.editor_voice_default),
                    subtitle = null,
                    selected = selectedName == null,
                    onClick = onDefault,
                )
                NeuDivider()
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(items = voices, key = { it.name }) { voice ->
                        VoiceOptionRow(
                            title = voice.displayName,
                            subtitle = buildString {
                                append(voice.localeTag)
                                when (VoiceCatalog.genderOf(voice)) {
                                    VoiceGender.Female -> append(" · 女")
                                    VoiceGender.Male -> append(" · 男")
                                    VoiceGender.Unknown -> Unit
                                }
                            },
                            selected = voice.name == selectedName,
                            onClick = { onSelect(voice) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceOptionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) palette.accentTint.copy(alpha = 0.55f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) palette.accent else palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
            }
        }
        if (selected) {
            VIconImage(icon = VIcon.Check, tint = palette.accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun editorMessageText(raw: String): String = when {
    raw == "content-required" -> stringResource(R.string.editor_content_required)
    raw == "recording-too-short" -> stringResource(R.string.editor_recording_too_short)
    raw == "recording-active" -> stringResource(R.string.editor_recording_locked)
    raw == "recording-not-found" -> stringResource(R.string.editor_recording_not_found)
    raw == "speech-empty" -> stringResource(R.string.editor_speech_empty)
    raw == "text-too-long" -> stringResource(R.string.editor_text_too_long)
    raw == "tts-unavailable" -> stringResource(R.string.editor_tts_unavailable)
    raw == "tts-failed" -> stringResource(R.string.editor_audio_failed, "TTS")
    raw == "mic-permission" -> stringResource(R.string.editor_mic_permission)
    raw.startsWith("recording-failed:") -> stringResource(
        R.string.editor_recording_failed,
        raw.substringAfter(':', "unknown"),
    )

    else -> raw
}
