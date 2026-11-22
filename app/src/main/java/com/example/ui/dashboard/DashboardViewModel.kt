package com.example.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmScheduler
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.ReminderRepository
import com.example.domain.models.AlarmEventType
import com.example.domain.models.AppPreferences
import com.example.domain.models.Category
import com.example.domain.models.PermissionHealth
import com.example.domain.models.Reminder
import com.example.domain.models.ReminderFilter
import com.example.utils.PermissionHelper
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DashboardUiState(
    val nextUpcoming: Reminder? = null,
    val todayReminders: List<Reminder> = emptyList(),
    val upcomingReminders: List<Reminder> = emptyList(),
    val completedReminders: List<Reminder> = emptyList(),
    val categories: List<Category> = emptyList(),
    val categoriesById: Map<Long, Category> = emptyMap(),
    val preferences: AppPreferences = AppPreferences(),
    val selectedFilter: ReminderFilter = ReminderFilter.ALL,
    val selectedCategoryId: Long? = null,
    val searchQuery: String = "",
    val permissionHealth: PermissionHealth = PermissionHealth(
        notificationsGranted = true,
        exactAlarmGranted = true,
        fullScreenIntentGranted = true
    )
)

class DashboardViewModel(
    private val appContext: Context,
    private val reminderRepository: ReminderRepository,
    preferencesRepository: PreferencesRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(ReminderFilter.ALL)
    private val selectedCategoryId = MutableStateFlow<Long?>(null)
    private val searchQuery = MutableStateFlow("")
    private val permissionHealth = MutableStateFlow(PermissionHelper.checkHealth(appContext))

    val uiState: StateFlow<DashboardUiState> = combine(
        reminderRepository.remindersFlow,
        reminderRepository.categoriesFlow,
        preferencesRepository.preferencesFlow,
        combine(selectedFilter, selectedCategoryId, searchQuery, permissionHealth) { f, c, q, p ->
            Quad(f, c, q, p)
        }
    ) { reminders, categories, prefs, controls ->
        val (filter, categoryId, query, health) = controls
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()
        val categoriesMap = categories.associateBy { it.id }

        val nextUp = reminders
            .filter { it.isEnabled && !it.isCompleted }
            .mapNotNull { r ->
                val trigger = r.effectiveNextTriggerMillis() ?: return@mapNotNull null
                if (trigger >= now - 60_000L) r to trigger else null
            }
            .minByOrNull { it.second }
            ?.first

        val normalizedQuery = query.trim().lowercase()
        val filtered = reminders.filter { r ->
            val catMatch = categoryId == null || r.categoryId == categoryId
            val queryMatch = normalizedQuery.isEmpty() ||
                r.title.lowercase().contains(normalizedQuery) ||
                r.description.lowercase().contains(normalizedQuery) ||
                (categoriesMap[r.categoryId]?.name?.lowercase()?.contains(normalizedQuery) == true)
            catMatch && queryMatch
        }

        val activeList = filtered.filter { !it.isCompleted }
        val todayList = activeList.filter { r ->
            val trigger = r.snoozedUntilMillis ?: r.scheduledTimeMillis
            val date = Instant.ofEpochMilli(trigger).atZone(zone).toLocalDate()
            !date.isAfter(today)
        }.sortedBy { it.snoozedUntilMillis ?: it.scheduledTimeMillis }

        val upcomingList = activeList.filter { r ->
            val trigger = r.snoozedUntilMillis ?: r.scheduledTimeMillis
            val date = Instant.ofEpochMilli(trigger).atZone(zone).toLocalDate()
            date.isAfter(today)
        }.sortedBy { it.snoozedUntilMillis ?: it.scheduledTimeMillis }

        val completedList = filtered.filter { it.isCompleted }
            .sortedByDescending { it.completedAtMillis ?: it.updatedAtMillis }

        DashboardUiState(
            nextUpcoming = nextUp,
            todayReminders = todayList,
            upcomingReminders = upcomingList,
            completedReminders = completedList,
            categories = categories,
            categoriesById = categoriesMap,
            preferences = prefs,
            selectedFilter = filter,
            selectedCategoryId = categoryId,
            searchQuery = query,
            permissionHealth = health
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(permissionHealth = PermissionHelper.checkHealth(appContext))
    )

    fun refreshPermissions() {
        permissionHealth.value = PermissionHelper.checkHealth(appContext)
    }

    fun setFilter(filter: ReminderFilter) {
        selectedFilter.value = filter
    }

    fun setCategoryFilter(categoryId: Long?) {
        selectedCategoryId.value = if (selectedCategoryId.value == categoryId) null else categoryId
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun toggleEnabled(reminder: Reminder, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val updated = reminder.copy(
                isEnabled = enabled,
                snoozedUntilMillis = if (!enabled) null else reminder.snoozedUntilMillis,
                updatedAtMillis = now
            )
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
                snoozedUntilMillis = null,
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

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
