package com.vocalarm.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocalarm.app.AppContainer
import com.vocalarm.app.R
import com.vocalarm.app.ui.TabBarSpacer
import com.vocalarm.app.ui.neu.NeuCard
import com.vocalarm.app.ui.neu.VIcon
import com.vocalarm.app.ui.neu.VIconImage
import com.vocalarm.app.ui.theme.LocalPalette
import com.vocalarm.app.ui.theme.Palettes
import com.vocalarm.app.ui.theme.VocalarmPalette

@Composable
fun AppearanceScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.appearance_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.appearance_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.appearance_palette),
                modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
            )
        }
        item {
            PaletteGrid(
                selectedId = settings.paletteId,
                onSelect = { id -> container.settingsRepository.setPalette(id) },
            )
        }
        item { PalettePreviewCard() }
        item {
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                radius = 22.dp,
                depth = 5.dp,
                contentPadding = PaddingValues(18.dp),
            ) {
                Text(
                    text = stringResource(R.string.appearance_tip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
            }
        }
        item { TabBarSpacer() }
    }
}

@Composable
private fun PaletteGrid(selectedId: String, onSelect: (String) -> Unit) {
    val rows = Palettes.all.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { palette ->
                    PaletteCard(
                        palette = palette,
                        selected = palette.id == selectedId,
                        onClick = { onSelect(palette.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PaletteCard(
    palette: VocalarmPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .height(124.dp)
            .clip(shape)
            .background(palette.background)
            .then(
                if (selected) {
                    Modifier.border(2.dp, palette.accent, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch(color = palette.background, outline = palette.darkShadow)
                Swatch(color = palette.accent)
                Swatch(color = palette.textPrimary)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(palette.nameRes),
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
            )
            Text(
                text = stringResource(
                    if (palette.isDark) R.string.appearance_dark_badge else R.string.appearance_light_badge,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
                contentAlignment = Alignment.Center,
            ) {
                VIconImage(
                    icon = VIcon.Check,
                    tint = palette.onAccent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, outline: Color? = null) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (outline != null) {
                    Modifier.border(1.dp, outline, CircleShape)
                } else {
                    Modifier
                },
            ),
    )
}

@Composable
private fun PalettePreviewCard() {
    val palette = LocalPalette.current
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 26.dp,
        depth = 7.dp,
        contentPadding = PaddingValues(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VIconImage(icon = VIcon.Bell, tint = palette.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.appearance_preview_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.appearance_preview_time),
                    style = MaterialTheme.typography.displayLarge,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.accent.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_alarm_on),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accent,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.appearance_preview_body),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewChip(stringResource(R.string.appearance_light_badge), selected = !palette.isDark)
                PreviewChip(stringResource(R.string.appearance_dark_badge), selected = palette.isDark)
            }
        }
    }
}

@Composable
private fun PreviewChip(text: String, selected: Boolean) {
    val palette = LocalPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) palette.accent.copy(alpha = 0.14f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) palette.accent else palette.muted,
        )
    }
}
