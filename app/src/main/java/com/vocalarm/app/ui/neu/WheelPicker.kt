package com.vocalarm.app.ui.neu

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vocalarm.app.ui.theme.LocalPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Snapping wheel built on LazyColumn. The centred row is the selected value, matching the visual
 * "recessed slot" of the neumorphic card behind it.
 */
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 46.dp,
    visibleCount: Int = 5,
) {
    val palette = LocalPalette.current
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val currentSelected by rememberUpdatedState(selectedIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val verticalPadding = itemHeight * (visibleCount / 2)

    LaunchedEffect(state, items.size) {
        snapshotFlow { state.isScrollInProgress to state.firstVisibleItemIndex }
            .filter { !it.first }
            .map { it.second }
            .distinctUntilChanged()
            .collect { index ->
                if (index in items.indices && index != currentSelected) {
                    onSelected(index)
                }
            }
    }

    Box(
        modifier = modifier.height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .neuSurface(
                    palette = palette,
                    fill = palette.background,
                    radius = 14.dp,
                    tone = NeuTone.Inset,
                    depth = 3.dp,
                    blur = 10.dp,
                ),
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = verticalPadding),
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(items) { index, label ->
                val selected = index == selectedIndex
                val color by animateColorAsState(
                    targetValue = if (selected) palette.textPrimary else palette.muted,
                    label = "wheelColor",
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = if (selected) 26.sp else 22.sp,
                        ),
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
fun TimeWheel(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val hours = remember { (0..23).map { it.toString().padStart(2, '0') } }
    val minutes = remember { (0..59).map { it.toString().padStart(2, '0') } }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        WheelPicker(
            items = hours,
            selectedIndex = hour.coerceIn(0, 23),
            onSelected = onHourChange,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = ":",
            style = MaterialTheme.typography.displayMedium,
            color = palette.muted,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        WheelPicker(
            items = minutes,
            selectedIndex = minute.coerceIn(0, 59),
            onSelected = onMinuteChange,
            modifier = Modifier.weight(1f),
        )
    }
}
