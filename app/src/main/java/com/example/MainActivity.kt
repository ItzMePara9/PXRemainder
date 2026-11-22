package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.models.AppPreferences
import com.example.ui.navigation.PXReminderAppShell
import com.example.ui.theme.PXReminderTheme
import com.example.widget.WidgetUpdater

class MainActivity : ComponentActivity() {

    private var openCreateOnLaunch by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openCreateOnLaunch = intent?.getBooleanExtra(WidgetUpdater.EXTRA_OPEN_CREATE, false) == true

        val app = application as PXReminderApplication

        setContent {
            val prefs by app.preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = AppPreferences()
            )
            PXReminderTheme(themeMode = prefs.themeMode) {
                PXReminderAppShell(
                    app = app,
                    openCreateOnLaunch = openCreateOnLaunch,
                    onConsumedOpenCreate = { openCreateOnLaunch = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(WidgetUpdater.EXTRA_OPEN_CREATE, false)) {
            openCreateOnLaunch = true
        }
    }
}
