package com.example.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.models.AlarmEventType
import com.example.domain.models.AlarmHistoryItem
import com.example.domain.models.Category
import com.example.domain.models.Priority
import com.example.domain.models.Reminder
import com.example.domain.models.RepeatType

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val iconKey: String,
    val colorHex: Long,
    val isDefault: Boolean = false
) {
    fun toDomain(): Category = Category(
        id = id,
        name = name,
        iconKey = iconKey,
        colorHex = colorHex,
        isDefault = isDefault
    )

    companion object {
        fun fromDomain(category: Category): CategoryEntity = CategoryEntity(
            id = category.id,
            name = category.name,
            iconKey = category.iconKey,
            colorHex = category.colorHex,
            isDefault = category.isDefault
        )
    }
}

@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["scheduledTimeMillis"]),
        Index(value = ["categoryId"]),
        Index(value = ["isEnabled", "isCompleted"])
    ]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String = "",
    val scheduledTimeMillis: Long,
    val additionalTimesCsv: String = "",
    val repeatType: String = RepeatType.NEVER.name,
    val customDaysMask: Int = 0,
    val categoryId: Long = 1L,
    val priority: String = Priority.MEDIUM.name,
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
    fun toDomain(): Reminder {
        val extras = if (additionalTimesCsv.isBlank()) {
            emptyList()
        } else {
            additionalTimesCsv.split(",")
                .mapNotNull { it.trim().toLongOrNull() }
                .filter { it > 0L }
        }
        return Reminder(
            id = id,
            title = title,
            description = description,
            scheduledTimeMillis = scheduledTimeMillis,
            additionalTimesMillis = extras,
            repeatType = RepeatType.fromString(repeatType),
            customDaysMask = customDaysMask,
            categoryId = categoryId,
            priority = Priority.fromString(priority),
            soundUri = soundUri,
            soundTitle = soundTitle,
            vibrationEnabled = vibrationEnabled,
            snoozeDurationMinutes = snoozeDurationMinutes,
            isEnabled = isEnabled,
            isCompleted = isCompleted,
            snoozedUntilMillis = snoozedUntilMillis,
            completedAtMillis = completedAtMillis,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        )
    }

    companion object {
        fun fromDomain(reminder: Reminder): ReminderEntity = ReminderEntity(
            id = reminder.id,
            title = reminder.title,
            description = reminder.description,
            scheduledTimeMillis = reminder.scheduledTimeMillis,
            additionalTimesCsv = reminder.additionalTimesMillis.joinToString(","),
            repeatType = reminder.repeatType.name,
            customDaysMask = reminder.customDaysMask,
            categoryId = reminder.categoryId,
            priority = reminder.priority.name,
            soundUri = reminder.soundUri,
            soundTitle = reminder.soundTitle,
            vibrationEnabled = reminder.vibrationEnabled,
            snoozeDurationMinutes = reminder.snoozeDurationMinutes,
            isEnabled = reminder.isEnabled,
            isCompleted = reminder.isCompleted,
            snoozedUntilMillis = reminder.snoozedUntilMillis,
            completedAtMillis = reminder.completedAtMillis,
            createdAtMillis = reminder.createdAtMillis,
            updatedAtMillis = reminder.updatedAtMillis
        )
    }
}

@Entity(
    tableName = "alarm_history",
    indices = [Index(value = ["timestampMillis"])]
)
data class AlarmHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val reminderId: Long,
    val reminderTitle: String,
    val categoryName: String,
    val categoryColorHex: Long,
    val eventType: String,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    fun toDomain(): AlarmHistoryItem = AlarmHistoryItem(
        id = id,
        reminderId = reminderId,
        reminderTitle = reminderTitle,
        categoryName = categoryName,
        categoryColorHex = categoryColorHex,
        eventType = AlarmEventType.fromString(eventType),
        timestampMillis = timestampMillis
    )

    companion object {
        fun fromDomain(item: AlarmHistoryItem): AlarmHistoryEntity = AlarmHistoryEntity(
            id = item.id,
            reminderId = item.reminderId,
            reminderTitle = item.reminderTitle,
            categoryName = item.categoryName,
            categoryColorHex = item.categoryColorHex,
            eventType = item.eventType.name,
            timestampMillis = item.timestampMillis
        )
    }
}
