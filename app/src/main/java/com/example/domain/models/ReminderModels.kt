package com.example.domain.models

enum class RepeatType(val label: String, val shortLabel: String) {
    NEVER("Never", "Once"),
    DAILY("Every day", "Daily"),
    WEEKDAYS("Every weekday", "Weekdays"),
    WEEKLY("Every week", "Weekly"),
    MONTHLY("Every month", "Monthly"),
    CUSTOM_DAYS("Custom days", "Custom");

    companion object {
        fun fromString(value: String?): RepeatType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NEVER
    }
}

enum class Priority(val label: String, val sortWeight: Int) {
    LOW("Low", 0),
    MEDIUM("Medium", 1),
    HIGH("High", 2),
    URGENT("Urgent", 3);

    companion object {
        fun fromString(value: String?): Priority =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
    }
}

enum class ThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System");

    companion object {
        fun fromString(value: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DARK
    }
}

enum class ReminderFilter(val label: String) {
    ALL("All"),
    TODAY("Today"),
    UPCOMING("Upcoming"),
    COMPLETED("Completed")
}

data class Category(
    val id: Long = 0L,
    val name: String,
    val iconKey: String,
    val colorHex: Long,
    val isDefault: Boolean = false
)

data class Reminder(
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val scheduledTimeMillis: Long,
    val additionalTimesMillis: List<Long> = emptyList(),
    val repeatType: RepeatType = RepeatType.NEVER,
    val customDaysMask: Int = 0,
    val categoryId: Long = 1L,
    val priority: Priority = Priority.MEDIUM,
    val soundUri: String = "builtin:classic_alarm",
    val soundTitle: String = "Classic Alarm",
    val vibrationEnabled: Boolean = true,
    val snoozeDurationMinutes: Int = 10,
    val isEnabled: Boolean = true,
    val isCompleted: Boolean = false,
    val snoozedUntilMillis: Long? = null,
    val completedAtMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun effectiveNextTriggerMillis(): Long? {
        if (!isEnabled || isCompleted) return null
        val candidates = buildList {
            add(snoozedUntilMillis ?: scheduledTimeMillis)
            addAll(additionalTimesMillis)
        }.filter { it > 0L }
        return candidates.minOrNull()
    }
}

data class AlarmHistoryItem(
    val id: Long = 0L,
    val reminderId: Long,
    val reminderTitle: String,
    val categoryName: String,
    val categoryColorHex: Long,
    val eventType: AlarmEventType,
    val timestampMillis: Long
)

enum class AlarmEventType(val label: String) {
    DISMISSED("Dismissed"),
    SNOOZED("Snoozed"),
    COMPLETED("Completed"),
    MISSED("Missed");

    companion object {
        fun fromString(value: String?): AlarmEventType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DISMISSED
    }
}

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val defaultSoundUri: String = "builtin:classic_alarm",
    val defaultSoundTitle: String = "Classic Alarm",
    val defaultAlarmVolume: Float = 0.85f,
    val defaultSnoozeMinutes: Int = 10,
    val defaultVibrationEnabled: Boolean = true,
    val gradualVolumeIncrease: Boolean = true,
    val snoozePlaySound: Boolean = true,
    val use24HourFormat: Boolean = false,
    val firstDayOfWeek: Int = 1 // 1 = Monday, 7 = Sunday (java.time.DayOfWeek.value)
)

data class AlarmSoundOption(
    val uri: String,
    val title: String,
    val subtitle: String,
    val isBuiltIn: Boolean,
    val rawResId: Int? = null
)

data class ActiveAlarm(
    val reminderId: Long,
    val slotIndex: Int,
    val title: String,
    val description: String,
    val scheduledTimeMillis: Long,
    val categoryName: String,
    val categoryColorHex: Long,
    val soundUri: String,
    val vibrationEnabled: Boolean,
    val snoozeDurationMinutes: Int,
    val isSnooze: Boolean = false,
    val triggeredAtMillis: Long = System.currentTimeMillis()
)

data class PermissionHealth(
    val notificationsGranted: Boolean,
    val exactAlarmGranted: Boolean,
    val fullScreenIntentGranted: Boolean
) {
    val allGranted: Boolean
        get() = notificationsGranted && exactAlarmGranted && fullScreenIntentGranted
}
