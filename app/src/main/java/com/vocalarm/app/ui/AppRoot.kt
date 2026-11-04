package com.vocalarm.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vocalarm.app.AppContainer
import com.vocalarm.app.ui.screens.AboutScreen
import com.vocalarm.app.ui.screens.AlarmEditScreen
import com.vocalarm.app.ui.screens.AlarmListScreen
import com.vocalarm.app.ui.screens.AppearanceScreen
import com.vocalarm.app.ui.theme.LocalPalette

@Composable
fun AppRoot(
    container: AppContainer,
    requestedTab: String?,
    onRequestedTabConsumed: () -> Unit,
) {
    val palette = LocalPalette.current
    var tab by rememberSaveable { mutableStateOf(Tab.Alarms) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(requestedTab) {
        when (requestedTab) {
            MainActivity.TAB_ABOUT -> {
                tab = Tab.About
                onRequestedTabConsumed()
            }

            null -> Unit
            else -> onRequestedTabConsumed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            when (tab) {
                Tab.Alarms -> AlarmListScreen(
                    container = container,
                    onEdit = { id ->
                        editingId = id
                        editorOpen = true
                    },
                    onOpenAppearance = { tab = Tab.Appearance },
                )

                Tab.Appearance -> AppearanceScreen(container)
                Tab.About -> AboutScreen(container)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            NeuTabBar(
                tabs = Tab.entries.toList(),
                selected = tab,
                onSelect = { tab = it },
            )
        }

        AnimatedVisibility(
            visible = editorOpen,
            enter = slideInVertically { height -> height },
            exit = slideOutVertically { height -> height },
        ) {
            AlarmEditScreen(
                container = container,
                alarmId = editingId,
                onClose = { editorOpen = false },
            )
        }
    }
}
