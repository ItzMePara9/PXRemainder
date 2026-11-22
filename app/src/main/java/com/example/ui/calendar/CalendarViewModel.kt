package com.example.ui.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmScheduler
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.ReminderRepository
import com.example.domain.models.AlarmEventType
import com.example.domain.models.AppPreferences
import com.example.domain.models.Category
import com.example.domain.models.Reminder
import com.example.domain.usecases.RecurrenceCalculator
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val displayedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val datesWithReminders: Set<LocalDate> = emptySet(),
    val selectedDateReminders: List<Reminder> = emptyList(),
    val categoriesById: Map<Long, Category> = emptyMap(),
    val preferences: AppPreferences = AppPreferences()
)

class CalendarViewModel(
    private val appContext: Context,
    private val reminderRepository: ReminderRepository,
    preferencesRepository: PreferencesRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val displayedMonth = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow(LocalDate.now())

    val uiState: StateFlow<CalendarUiState> = combine(
        reminderRepository.remindersFlow,
        reminderRepository.categoriesFlow,
        preferencesRepository.preferencesFlow,
        displayedMonth,
        selectedDate
    ) { reminders, categories, prefs, month, date ->
        val categoriesMap = categories.associateBy { it.id }
        val activeOrScheduled = reminders.filter { !it.isCompleted || RecurrenceCalculator.occursOnDate(it, date) }

        val markedDates = mutableSetOf<LocalDate>()
        for (day in 1..month.lengthOfMonth()) {
            val d = month.atDay(day)
            if (reminders.any { !it.isCompleted && RecurrenceCalculator.occursOnDate(it, d) }) {
                markedDates.add(d)
            }
        }

        val dayReminders = activeOrScheduled
            .filter { RecurrenceCalculator.occursOnDate(it, date) }
            .sortedWith(compareBy<Reminder> { it.isCompleted }.thenBy { it.scheduledTimeMillis })

        CalendarUiState(
            displayedMonth = month,
            selectedDate = date,
            datesWithReminders = markedDates,
            selectedDateReminders = dayReminders,
            categoriesById = categoriesMap,
            preferences = prefs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState()
    )

    fun previousMonth() {
        displayedMonth.value = displayedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        displayedMonth.value = displayedMonth.value.plusMonths(1)
    }

    fun jumpToToday() {
        val today = LocalDate.now()
        displayedMonth.value = YearMonth.from(today)
        selectedDate.value = today
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        displayedMonth.value = YearMonth.from(date)
    }

    fun toggleEnabled(reminder: Reminder, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = reminder.copy(isEnabled = enabled, updatedAtMillis = System.currentTimeMillis())
            reminderRepository.saveReminder(updated)
            if (enabled) {
                val cat = reminderRepository.getCategoryById(updated.categoryId)
                alarmScheduler.scheduleReminder(updated, cat)
            } else {
                alarmScheduler.cancelReminder(updated.id)
            }
            WidgetUpdater.updateAll(appContext)
        }
    }

    fun toggleCompleted(reminder: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val markingComplete = !reminder.isCompleted
            val updated = reminder.copy(
                isCompleted = markingComplete,
                isEnabled = !markingComplete,
                completedAtMillis = if (markingComplete) now else null,
                updatedAtMillis = now
            )
            reminderRepository.saveReminder(updated)
            val cat = reminderRepository.getCategoryById(reminder.categoryId)
            if (markingComplete) {
                alarmScheduler.cancelReminder(updated.id)
                reminderRepository.recordHistory(
                    reminderId = reminder.id,
                    title = reminder.title,
                    categoryName = cat?.name ?: "Reminder",
                    categoryColorHex = cat?.colorHex ?: 0xFFA78BFA,
                    eventType = AlarmEventType.COMPLETED
                )
            } else if (updated.scheduledTimeMillis > now) {
                alarmScheduler.scheduleReminder(updated, cat)
            }
            WidgetUpdater.updateAll(appContext)
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            alarmScheduler.cancelReminder(reminder.id)
            reminderRepository.deleteReminder(reminder.id)
            WidgetUpdater.updateAll(appContext)
        }
    }
}
