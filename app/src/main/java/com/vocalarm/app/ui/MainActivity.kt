package com.vocalarm.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocalarm.app.VocalarmApp
import com.vocalarm.app.ui.theme.Palettes
import com.vocalarm.app.ui.theme.VocalarmTheme

class MainActivity : ComponentActivity() {

    private var requestedTab by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTab = intent.getStringExtra(EXTRA_TAB)
        val container = (application as VocalarmApp).container
        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
            VocalarmTheme(palette = Palettes.byId(settings.paletteId)) {
                AppRoot(
                    container = container,
                    requestedTab = requestedTab,
                    onRequestedTabConsumed = { requestedTab = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTab = intent.getStringExtra(EXTRA_TAB)
    }

    companion object {
        const val EXTRA_TAB = "extra_tab"
        const val TAB_ABOUT = "about"
    }
}
