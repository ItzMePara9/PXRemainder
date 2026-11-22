package com.example.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmScheduler
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.ReminderRepository
import com.example.domain.models.AppPreferences
import com.example.domain.models.Category
import com.example.domain.models.PermissionHealth
import com.example.domain.models.ThemeMode
import com.example.utils.BackupManager
import com.example.utils.BackupParseResult
import com.example.utils.PermissionHelper
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences(),
    val categories: List<Category> = emptyList(),
    val permissionHealth: PermissionHealth = PermissionHealth(true, true, true),
    val completedCount: Int = 0,
    val statusMessage: String? = null
)

class SettingsViewModel(
    private val appContext: Context,
    private val reminderRepository: ReminderRepository,
    private val preferencesRepository: PreferencesRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val permissionHealth = MutableStateFlow(PermissionHelper.checkHealth(appContext))
    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferencesFlow,
        reminderRepository.categoriesFlow,
        reminderRepository.remindersFlow,
        permissionHealth,
        statusMessage
    ) { prefs, categories, reminders, health, message ->
        SettingsUiState(
            preferences = prefs,
            categories = categories,
            permissionHealth = health,
            completedCount = reminders.count { it.isCompleted },
            statusMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(permissionHealth = PermissionHelper.checkHealth(appContext))
    )

    fun refreshPermissions() {
        permissionHealth.value = PermissionHelper.checkHealth(appContext)
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setDefaultSound(uri: String, title: String) {
        viewModelScope.launch { preferencesRepository.setDefaultSound(uri, title) }
    }

    fun setDefaultVolume(volume: Float) {
        viewModelScope.launch { preferencesRepository.setDefaultVolume(volume) }
    }

    fun setDefaultSnoozeMinutes(minutes: Int) {
        viewModelScope.launch { preferencesRepository.setDefaultSnoozeMinutes(minutes) }
    }

    fun setDefaultVibration(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDefaultVibrationEnabled(enabled) }
    }

    fun setGradualVolume(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setGradualVolumeIncrease(enabled) }
    }

    fun setSnoozePlaySound(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSnoozePlaySound(enabled) }
    }

    fun setUse24Hour(use24Hour: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setUse24HourFormat(use24Hour)
            WidgetUpdater.updateAll(appContext)
        }
    }

    fun setFirstDayOfWeek(dayOfWeekValue: Int) {
        viewModelScope.launch { preferencesRepository.setFirstDayOfWeek(dayOfWeekValue) }
    }

    fun saveCategory(category: Category) {
        viewModelScope.launch(Dispatchers.IO) {
            reminderRepository.saveCategory(category)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch(Dispatchers.IO) {
            reminderRepository.deleteCategory(category)
        }
    }

    fun deleteAllCompleted() {
        viewModelScope.launch(Dispatchers.IO) {
            reminderRepository.deleteAllCompleted()
            WidgetUpdater.updateAll(appContext)
            statusMessage.value = "Cleared all completed reminders."
        }
    }

    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val categories = reminderRepository.getAllCategoriesSnapshot()
                val reminders = reminderRepository.getAllRemindersSnapshot()
                val prefs = preferencesRepository.getSnapshot()
                val json = BackupManager.exportToJson(categories, reminders, prefs)
                appContext.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                    stream.flush()
                } ?: error("Unable to open destination file.")
                statusMessage.value = "Exported ${reminders.size} reminders to JSON backup."
            } catch (e: Exception) {
                statusMessage.value = "Backup export failed: ${e.localizedMessage ?: "unknown error"}"
            }
        }
    }

    fun importBackupFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: error("Unable to read selected file.")

                when (val result = BackupManager.parseAndValidate(json)) {
                    is BackupParseResult.Invalid -> {
                        statusMessage.value = result.reason
                    }
                    is BackupParseResult.Valid -> {
                        val existing = reminderRepository.getAllRemindersSnapshot()
                        existing.forEach { alarmScheduler.cancelReminder(it.id) }

                        reminderRepository.replaceAllDataForRestore(result.categories, result.reminders)
                        preferencesRepository.applyBackupPreferences(result.preferences)

                        val active = reminderRepository.getAllActiveReminders()
                        val categoriesById = reminderRepository.getAllCategoriesSnapshot().associateBy { it.id }
                        val updated = alarmScheduler.rescheduleAll(active, categoriesById)
                        updated.forEach { reminderRepository.saveReminder(it) }
                        WidgetUpdater.updateAll(appContext)

                        statusMessage.value = "Restored ${result.reminders.size} reminders and ${result.categories.size} categories."
                    }
                }
            } catch (e: Exception) {
                statusMessage.value = "Restore failed: ${e.localizedMessage ?: "unknown error"}"
            }
        }
    }

    fun scheduleTestAlarm5s() {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = preferencesRepository.getSnapshot()
            val testTime = System.currentTimeMillis() + 5_000L
            val testReminder = com.example.domain.models.Reminder(
                id = com.example.alarm.AlarmService.TEST_ALARM_ID,
                title = "PXReminder Test Alarm",
                description = "Full-screen alarm engine verification",
                scheduledTimeMillis = testTime,
                soundUri = prefs.defaultSoundUri,
                soundTitle = prefs.defaultSoundTitle,
                vibrationEnabled = prefs.defaultVibrationEnabled,
                snoozeDurationMinutes = prefs.defaultSnoozeMinutes,
                isEnabled = true,
                isCompleted = false
            )
            alarmScheduler.scheduleReminder(testReminder)
            statusMessage.value = "Test alarm scheduled for 5 seconds. Lock your screen or stay here to verify."
        }
    }
}
