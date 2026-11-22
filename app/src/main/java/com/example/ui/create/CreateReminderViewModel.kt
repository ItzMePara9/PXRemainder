package com.example.ui.create

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmScheduler
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.ReminderRepository
import com.example.domain.models.AppPreferences
import com.example.domain.models.Category
import com.example.domain.models.Priority
import com.example.domain.models.Reminder
import com.example.domain.models.RepeatType
import com.example.domain.usecases.RecurrenceCalculator
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class ScheduleSlotUi(
    val date: LocalDate,
    val time: LocalTime
) {
    fun toEpochMillis(zoneId: ZoneId = ZoneId.systemDefault()): Long =
        LocalDateTime.of(date, time).atZone(zoneId).toInstant().toEpochMilli()
}

data class CreateReminderUiState(
    val reminderId: Long = 0L,
    val isEditing: Boolean = false,
    val title: String = "",
    val description: String = "",
    val primaryDate: LocalDate = LocalDate.now(),
    val primaryTime: LocalTime = LocalTime.now().plusMinutes(5).withSecond(0).withNano(0),
    val additionalSlots: List<ScheduleSlotUi> = emptyList(),
    val repeatType: RepeatType = RepeatType.NEVER,
    val customDaysMask: Int = 0,
    val categoryId: Long = 1L,
    val priority: Priority = Priority.MEDIUM,
    val soundUri: String = "builtin:classic_alarm",
    val soundTitle: String = "Classic Alarm",
    val vibrationEnabled: Boolean = true,
    val snoozeDurationMinutes: Int = 10,
    val categories: List<Category> = emptyList(),
    val preferences: AppPreferences = AppPreferences(),
    val titleError: String? = null,
    val scheduleError: String? = null,
    val isLoaded: Boolean = false,
    val isSaving: Boolean = false
)

class CreateReminderViewModel(
    private val appContext: Context,
    private val reminderRepository: ReminderRepository,
    private val preferencesRepository: PreferencesRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateReminderUiState())
    val uiState: StateFlow<CreateReminderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            reminderRepository.categoriesFlow.collect { cats ->
                _uiState.update { state ->
                    val validCatId = if (cats.any { it.id == state.categoryId }) {
                        state.categoryId
                    } else {
                        cats.firstOrNull()?.id ?: 1L
                    }
                    state.copy(categories = cats, categoryId = validCatId)
                }
            }
        }
    }

    fun initialize(reminderId: Long?, initialDateMillis: Long?) {
        if (_uiState.value.isLoaded) return
        viewModelScope.launch {
            val prefs = withContext(Dispatchers.IO) { preferencesRepository.getSnapshot() }
            val cats = withContext(Dispatchers.IO) { reminderRepository.getAllCategoriesSnapshot() }

            if (reminderId != null && reminderId > 0L) {
                val existing = withContext(Dispatchers.IO) { reminderRepository.getReminderById(reminderId) }
                if (existing != null) {
                    val zone = ZoneId.systemDefault()
                    val primaryZdt = Instant.ofEpochMilli(existing.scheduledTimeMillis).atZone(zone)
                    val extras = existing.additionalTimesMillis.map { millis ->
                        val zdt = Instant.ofEpochMilli(millis).atZone(zone)
                        ScheduleSlotUi(zdt.toLocalDate(), zdt.toLocalTime())
                    }
                    _uiState.update {
                        it.copy(
                            reminderId = existing.id,
                            isEditing = true,
                            title = existing.title,
                            description = existing.description,
                            primaryDate = primaryZdt.toLocalDate(),
                            primaryTime = primaryZdt.toLocalTime().withSecond(0).withNano(0),
                            additionalSlots = extras,
                            repeatType = existing.repeatType,
                            customDaysMask = existing.customDaysMask,
                            categoryId = existing.categoryId,
                            priority = existing.priority,
                            soundUri = existing.soundUri,
                            soundTitle = existing.soundTitle,
                            vibrationEnabled = existing.vibrationEnabled,
                            snoozeDurationMinutes = existing.snoozeDurationMinutes,
                            categories = cats,
                            preferences = prefs,
                            isLoaded = true
                        )
                    }
                    return@launch
                }
            }

            val defaultDate = if (initialDateMillis != null && initialDateMillis > 0L) {
                Instant.ofEpochMilli(initialDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            } else {
                LocalDate.now()
            }
            val nowTime = LocalTime.now().plusMinutes(5).withSecond(0).withNano(0)
            _uiState.update {
                it.copy(
                    primaryDate = defaultDate,
                    primaryTime = nowTime,
                    categoryId = cats.firstOrNull()?.id ?: 1L,
                    soundUri = prefs.defaultSoundUri,
                    soundTitle = prefs.defaultSoundTitle,
                    vibrationEnabled = prefs.defaultVibrationEnabled,
                    snoozeDurationMinutes = prefs.defaultSnoozeMinutes,
                    categories = cats,
                    preferences = prefs,
                    isLoaded = true
                )
            }
        }
    }

    fun updateTitle(value: String) {
        _uiState.update { it.copy(title = value, titleError = null) }
    }

    fun updateDescription(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    fun updatePrimaryDate(date: LocalDate) {
        _uiState.update { it.copy(primaryDate = date, scheduleError = null) }
    }

    fun updatePrimaryTime(time: LocalTime) {
        _uiState.update { it.copy(primaryTime = time.withSecond(0).withNano(0), scheduleError = null) }
    }

    fun addScheduleSlot(date: LocalDate, time: LocalTime) {
        val normalizedTime = time.withSecond(0).withNano(0)
        val slot = ScheduleSlotUi(date, normalizedTime)
        _uiState.update { state ->
            if (state.additionalSlots.size >= AlarmScheduler.MAX_SLOTS_PER_REMINDER - 1) return@update state
            if (slot.date == state.primaryDate && slot.time == state.primaryTime) return@update state
            if (state.additionalSlots.any { it.date == slot.date && it.time == slot.time }) return@update state
            state.copy(additionalSlots = state.additionalSlots + slot, scheduleError = null)
        }
    }

    fun removeScheduleSlot(index: Int) {
        _uiState.update { state ->
            state.copy(
                additionalSlots = state.additionalSlots.filterIndexed { i, _ -> i != index },
                scheduleError = null
            )
        }
    }

    fun updateRepeatType(type: RepeatType) {
        _uiState.update { state ->
            val mask = if (type == RepeatType.CUSTOM_DAYS && state.customDaysMask == 0) {
                RecurrenceCalculator.dayBit(state.primaryDate.dayOfWeek)
            } else {
                state.customDaysMask
            }
            state.copy(repeatType = type, customDaysMask = mask, scheduleError = null)
        }
    }

    fun toggleCustomDay(dayOfWeek: DayOfWeek) {
        _uiState.update { state ->
            state.copy(
                customDaysMask = RecurrenceCalculator.toggleDay(state.customDaysMask, dayOfWeek),
                scheduleError = null
            )
        }
    }

    fun updateCategory(categoryId: Long) {
        _uiState.update { it.copy(categoryId = categoryId) }
    }

    fun saveCustomCategory(category: Category) {
        viewModelScope.launch(Dispatchers.IO) {
            val savedId = reminderRepository.saveCategory(category)
            _uiState.update { it.copy(categoryId = savedId) }
        }
    }

    fun updatePriority(priority: Priority) {
        _uiState.update { it.copy(priority = priority) }
    }

    fun updateSound(uri: String, title: String) {
        _uiState.update { it.copy(soundUri = uri, soundTitle = title) }
    }

    fun updateVibration(enabled: Boolean) {
        _uiState.update { it.copy(vibrationEnabled = enabled) }
    }

    fun updateSnoozeDuration(minutes: Int) {
        _uiState.update { it.copy(snoozeDurationMinutes = minutes.coerceIn(1, 60)) }
    }

    fun saveReminder(onSuccess: () -> Unit) {
        val state = _uiState.value
        val trimmedTitle = state.title.trim()
        if (trimmedTitle.isEmpty()) {
            _uiState.update { it.copy(titleError = "Enter a reminder title.") }
            return
        }

        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val primaryMillis = ScheduleSlotUi(state.primaryDate, state.primaryTime).toEpochMillis(zone)
        val extraMillis = state.additionalSlots.map { it.toEpochMillis(zone) }

        if (state.repeatType == RepeatType.CUSTOM_DAYS && state.customDaysMask == 0) {
            _uiState.update { it.copy(scheduleError = "Select at least one day for custom repeat.") }
            return
        }

        if (state.repeatType == RepeatType.NEVER) {
            if (primaryMillis <= now) {
                _uiState.update {
                    it.copy(scheduleError = "Scheduled time cannot be in the past.")
                }
                return
            }
            if (extraMillis.any { it <= now }) {
                _uiState.update {
                    it.copy(scheduleError = "Additional schedule times must be in the future.")
                }
                return
            }
        }

        val effectivePrimary = if (state.repeatType != RepeatType.NEVER && primaryMillis <= now) {
            RecurrenceCalculator.nextOccurrenceAfter(
                baseMillis = primaryMillis,
                repeatType = state.repeatType,
                customDaysMask = state.customDaysMask,
                afterMillis = now,
                zoneId = zone
            ) ?: primaryMillis
        } else {
            primaryMillis
        }

        val effectiveExtras = extraMillis.map { slot ->
            if (state.repeatType != RepeatType.NEVER && slot <= now) {
                RecurrenceCalculator.nextOccurrenceAfter(
                    baseMillis = slot,
                    repeatType = state.repeatType,
                    customDaysMask = state.customDaysMask,
                    afterMillis = now,
                    zoneId = zone
                ) ?: slot
            } else {
                slot
            }
        }

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val existing = if (state.isEditing) reminderRepository.getReminderById(state.reminderId) else null
            val reminder = Reminder(
                id = state.reminderId,
                title = trimmedTitle,
                description = state.description.trim(),
                scheduledTimeMillis = effectivePrimary,
                additionalTimesMillis = effectiveExtras,
                repeatType = state.repeatType,
                customDaysMask = state.customDaysMask,
                categoryId = state.categoryId,
                priority = state.priority,
                soundUri = state.soundUri,
                soundTitle = state.soundTitle,
                vibrationEnabled = state.vibrationEnabled,
                snoozeDurationMinutes = state.snoozeDurationMinutes,
                isEnabled = true,
                isCompleted = false,
                snoozedUntilMillis = null,
                completedAtMillis = null,
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now
            )

            val savedId = reminderRepository.saveReminder(reminder)
            val category = reminderRepository.getCategoryById(reminder.categoryId)
            alarmScheduler.scheduleReminder(reminder.copy(id = savedId), category)
            WidgetUpdater.updateAll(appContext)

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isSaving = false) }
                onSuccess()
            }
        }
    }

    fun deleteReminder(onDeleted: () -> Unit) {
        val id = _uiState.value.reminderId
        if (id <= 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            alarmScheduler.cancelReminder(id)
            reminderRepository.deleteReminder(id)
            WidgetUpdater.updateAll(appContext)
            withContext(Dispatchers.Main) {
                onDeleted()
            }
        }
    }
}
