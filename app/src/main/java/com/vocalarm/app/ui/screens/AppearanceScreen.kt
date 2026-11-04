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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocalarm.app.AppContainer
import com.vocalarm.app.R
import com.vocalarm.app.ui.Eyebrow
import com.vocalarm.app.ui.TabBarSpacer
import com.vocalarm.app.ui.neu.NeuCard
import com.vocalarm.app.ui.neu.NeuIconTile
import com.vocalarm.app.ui.neu.VIcon
import com.vocalarm.app.ui.neu.VIconImage
import com.vocalarm.app.ui.theme.LocalPalette
import com.vocalarm.app.ui.theme.Palettes
import com.vocalarm.app.ui.theme.TimeDisplayStyle
import com.vocalarm.app.ui.theme.VocalarmPalette

@Composable
fun AppearanceScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column {
                Eyebrow(text = "VOCALARM", color = palette.accent)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.appearance_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                    color = palette.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.appearance_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.appearance_palette),
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
            )
        }

        item {
            PaletteGrid(
                selectedId = settings.paletteId,
                onSelect = { id -> container.settingsRepository.setPalette(id) },
            )
        }

        item {
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                radius = 22.dp,
                depth = 5.dp,
                contentPadding = PaddingValues(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuIconTile(icon = VIcon.Droplet, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.appearance_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
            }
        }

        item { TabBarSpacer() }
    }
}

@Composable
private fun PaletteGrid(selectedId: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Palettes.all.chunked(2).forEach { row ->
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
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .height(164.dp)
            .clip(shape)
            .background(palette.background)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) palette.accent else palette.darkShadow.copy(alpha = 0.35f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "07:30",
                    style = TimeDisplayStyle.copy(fontSize = 28.sp, lineHeight = 32.sp),
                    color = palette.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(palette.accent),
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.darkShadow.copy(alpha = 0.18f), RoundedCornerShape(13.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .width(34.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(palette.accent),
                        )
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier
                                .width(52.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(palette.textSecondary.copy(alpha = 0.35f)),
                        )
                    }
                    Box(
                        Modifier
                            .width(24.dp)
                            .height(13.dp)
                            .clip(CircleShape)
                            .background(palette.accent.copy(alpha = 0.85f)),
                    )
                }
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
                    .size(21.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
                contentAlignment = Alignment.Center,
            ) {
                VIconImage(
                    icon = VIcon.Check,
                    tint = palette.onAccent,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}
