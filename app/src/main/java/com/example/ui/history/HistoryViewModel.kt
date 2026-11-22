package com.example.ui.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmScheduler
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.ReminderRepository
import com.example.domain.models.AlarmHistoryItem
import com.example.domain.models.AppPreferences
import com.example.domain.models.Category
import com.example.domain.models.Reminder
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val completedReminders: List<Reminder> = emptyList(),
    val alarmEvents: List<AlarmHistoryItem> = emptyList(),
    val categoriesById: Map<Long, Category> = emptyMap(),
    val preferences: AppPreferences = AppPreferences()
)

class HistoryViewModel(
    private val appContext: Context,
    private val reminderRepository: ReminderRepository,
    preferencesRepository: PreferencesRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(
        reminderRepository.remindersFlow,
        reminderRepository.historyFlow,
        reminderRepository.categoriesFlow,
        preferencesRepository.preferencesFlow
    ) { reminders, events, categories, prefs ->
        HistoryUiState(
            completedReminders = reminders.filter { it.isCompleted }
                .sortedByDescending { it.completedAtMillis ?: it.updatedAtMillis },
            alarmEvents = events,
            categoriesById = categories.associateBy { it.id },
            preferences = prefs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
    )

    fun restoreCompletedReminder(reminder: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val updated = reminder.copy(
                isCompleted = false,
                isEnabled = reminder.scheduledTimeMillis > now,
                completedAtMillis = null,
                updatedAtMillis = now
            )
            reminderRepository.saveReminder(updated)
            if (updated.isEnabled) {
                val cat = reminderRepository.getCategoryById(updated.categoryId)
                alarmScheduler.scheduleReminder(updated, cat)
            }
            WidgetUpdater.updateAll(appContext)
        }
    }

    fun deleteCompletedReminder(reminder: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            alarmScheduler.cancelReminder(reminder.id)
            reminderRepository.deleteReminder(reminder.id)
            WidgetUpdater.updateAll(appContext)
        }
    }

    fun clearAllCompleted() {
        viewModelScope.launch(Dispatchers.IO) {
            reminderRepository.deleteAllCompleted()
            WidgetUpdater.updateAll(appContext)
        }
    }

    fun clearEventLog() {
        viewModelScope.launch(Dispatchers.IO) {
            reminderRepository.clearHistory()
        }
    }
}
