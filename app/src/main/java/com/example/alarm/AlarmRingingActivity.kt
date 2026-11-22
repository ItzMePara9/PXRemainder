package com.example.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.PXReminderApplication
import com.example.domain.models.ActiveAlarm
import com.example.domain.models.AppPreferences
import com.example.ui.ringing.AlarmRingingScreen
import com.example.ui.theme.PXReminderTheme

class AlarmRingingActivity : ComponentActivity() {

    private var hasSeenActiveAlarm by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenDisplay()
        hydrateFromIntent(intent)
        enableEdgeToEdge()

        val app = application as PXReminderApplication

        setContent {
            val activeAlarms by AlarmStateHolder.activeAlarms.collectAsStateWithLifecycle()
            val prefs by app.preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = AppPreferences()
            )

            BackHandler(enabled = activeAlarms.isNotEmpty()) { }

            LaunchedEffect(activeAlarms) {
                if (activeAlarms.isNotEmpty()) {
                    hasSeenActiveAlarm = true
                } else if (hasSeenActiveAlarm) {
                    finish()
                }
            }

            PXReminderTheme(themeMode = prefs.themeMode) {
                AlarmRingingScreen(
                    activeAlarms = activeAlarms,
                    use24Hour = prefs.use24HourFormat,
                    onSnooze = { alarm, minutes -> sendAlarmAction(AlarmScheduler.ACTION_ALARM_SNOOZE, alarm, minutes) },
                    onDismiss = { alarm -> sendAlarmAction(AlarmScheduler.ACTION_ALARM_DISMISS, alarm, 0) },
                    onDismissAll = { sendDismissAllAction() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureLockScreenDisplay()
        hydrateFromIntent(intent)
    }

    private fun hydrateFromIntent(intent: Intent?) {
        val reminderId = intent?.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L) ?: -1L
        if (reminderId > 0L && AlarmStateHolder.activeAlarms.value.isEmpty()) {
            val alarm = ActiveAlarm(
                reminderId = reminderId,
                slotIndex = intent?.getIntExtra(AlarmScheduler.EXTRA_SLOT_INDEX, 0) ?: 0,
                title = intent?.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "PXReminder Alarm",
                description = intent?.getStringExtra(AlarmScheduler.EXTRA_DESCRIPTION) ?: "",
                scheduledTimeMillis = intent?.getLongExtra(
                    AlarmScheduler.EXTRA_SCHEDULED_MILLIS,
                    System.currentTimeMillis()
                ) ?: System.currentTimeMillis(),
                categoryName = intent?.getStringExtra(AlarmScheduler.EXTRA_CATEGORY_NAME) ?: "Reminder",
                categoryColorHex = intent?.getLongExtra(AlarmScheduler.EXTRA_CATEGORY_COLOR, 0xFFA78BFA)
                    ?: 0xFFA78BFA,
                soundUri = intent?.getStringExtra(AlarmScheduler.EXTRA_SOUND_URI) ?: "builtin:classic_alarm",
                vibrationEnabled = intent?.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATION_ENABLED, true) ?: true,
                snoozeDurationMinutes = intent?.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_MINUTES, 10) ?: 10,
                isSnooze = intent?.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false) ?: false
            )
            AlarmStateHolder.addOrUpdate(alarm)
            hasSeenActiveAlarm = true
        } else if (AlarmStateHolder.activeAlarms.value.isNotEmpty()) {
            hasSeenActiveAlarm = true
        }
    }

    private fun configureLockScreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun sendAlarmAction(action: String, alarm: ActiveAlarm, snoozeMinutes: Int) {
        val serviceIntent = Intent(this, AlarmService::class.java).apply {
            this.action = action
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, alarm.reminderId)
            putExtra(AlarmScheduler.EXTRA_SLOT_INDEX, alarm.slotIndex)
            if (snoozeMinutes > 0) {
                putExtra(AlarmScheduler.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            }
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun sendDismissAllAction() {
        val serviceIntent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_DISMISS_ALL
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }
}
