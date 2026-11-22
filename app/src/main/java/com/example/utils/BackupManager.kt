package com.example.utils

import com.example.domain.models.AppPreferences
import com.example.domain.models.Category
import com.example.domain.models.Priority
import com.example.domain.models.Reminder
import com.example.domain.models.RepeatType
import com.example.domain.models.ThemeMode
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class BackupPayload(
    val formatVersion: Int = 1,
    val app: String = "PXReminder",
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val categories: List<BackupCategory>,
    val reminders: List<BackupReminder>,
    val preferences: BackupPreferences
)

@JsonClass(generateAdapter = true)
data class BackupCategory(
    val id: Long,
    val name: String,
    val iconKey: String,
    val colorHex: Long,
    val isDefault: Boolean
)

@JsonClass(generateAdapter = true)
data class BackupReminder(
    val id: Long,
    val title: String,
    val description: String,
    val scheduledTimeMillis: Long,
    val additionalTimesMillis: List<Long>,
    val repeatType: String,
    val customDaysMask: Int,
    val categoryId: Long,
    val priority: String,
    val soundUri: String,
    val soundTitle: String,
    val vibrationEnabled: Boolean,
    val snoozeDurationMinutes: Int,
    val isEnabled: Boolean,
    val isCompleted: Boolean,
    val snoozedUntilMillis: Long?,
    val completedAtMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@JsonClass(generateAdapter = true)
data class BackupPreferences(
    val themeMode: String,
    val defaultSoundUri: String,
    val defaultSoundTitle: String,
    val defaultAlarmVolume: Float,
    val defaultSnoozeMinutes: Int,
    val defaultVibrationEnabled: Boolean,
    val gradualVolumeIncrease: Boolean,
    val snoozePlaySound: Boolean,
    val use24HourFormat: Boolean,
    val firstDayOfWeek: Int
)

sealed class BackupParseResult {
    data class Valid(
        val categories: List<Category>,
        val reminders: List<Reminder>,
        val preferences: AppPreferences
    ) : BackupParseResult()

    data class Invalid(val reason: String) : BackupParseResult()
}

object BackupManager {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val adapter by lazy { moshi.adapter(BackupPayload::class.java).indent("  ") }

    fun exportToJson(
        categories: List<Category>,
        reminders: List<Reminder>,
        preferences: AppPreferences
    ): String {
        val payload = BackupPayload(
            categories = categories.map {
                BackupCategory(
                    id = it.id,
                    name = it.name,
                    iconKey = it.iconKey,
                    colorHex = it.colorHex,
                    isDefault = it.isDefault
                )
            },
            reminders = reminders.map {
                BackupReminder(
                    id = it.id,
                    title = it.title,
                    description = it.description,
                    scheduledTimeMillis = it.scheduledTimeMillis,
                    additionalTimesMillis = it.additionalTimesMillis,
                    repeatType = it.repeatType.name,
                    customDaysMask = it.customDaysMask,
                    categoryId = it.categoryId,
                    priority = it.priority.name,
                    soundUri = it.soundUri,
                    soundTitle = it.soundTitle,
                    vibrationEnabled = it.vibrationEnabled,
                    snoozeDurationMinutes = it.snoozeDurationMinutes,
                    isEnabled = it.isEnabled,
                    isCompleted = it.isCompleted,
                    snoozedUntilMillis = it.snoozedUntilMillis,
                    completedAtMillis = it.completedAtMillis,
                    createdAtMillis = it.createdAtMillis,
                    updatedAtMillis = it.updatedAtMillis
                )
            },
            preferences = BackupPreferences(
                themeMode = preferences.themeMode.name,
                defaultSoundUri = preferences.defaultSoundUri,
                defaultSoundTitle = preferences.defaultSoundTitle,
                defaultAlarmVolume = preferences.defaultAlarmVolume,
                defaultSnoozeMinutes = preferences.defaultSnoozeMinutes,
                defaultVibrationEnabled = preferences.defaultVibrationEnabled,
                gradualVolumeIncrease = preferences.gradualVolumeIncrease,
                snoozePlaySound = preferences.snoozePlaySound,
                use24HourFormat = preferences.use24HourFormat,
                firstDayOfWeek = preferences.firstDayOfWeek
            )
        )
        return adapter.toJson(payload)
    }

    fun parseAndValidate(json: String): BackupParseResult {
        if (json.isBlank()) {
            return BackupParseResult.Invalid("Backup file is empty.")
        }
        val payload = try {
            adapter.fromJson(json)
        } catch (e: Exception) {
            return BackupParseResult.Invalid("Invalid JSON format: ${e.localizedMessage ?: "parse error"}")
        } ?: return BackupParseResult.Invalid("Unrecognized backup structure.")

        if (payload.app != "PXReminder" || payload.formatVersion < 1) {
            return BackupParseResult.Invalid("File is not a valid PXReminder backup.")
        }

        if (payload.categories.any { it.name.isBlank() || it.id <= 0L }) {
            return BackupParseResult.Invalid("Backup contains invalid category entries.")
        }

        if (payload.reminders.any { it.title.isBlank() || it.scheduledTimeMillis <= 0L }) {
            return BackupParseResult.Invalid("Backup contains reminders with missing title or schedule.")
        }

        val categories = payload.categories.map {
            Category(
                id = it.id,
                name = it.name.trim(),
                iconKey = it.iconKey.ifBlank { "label" },
                colorHex = it.colorHex,
                isDefault = it.isDefault
            )
        }
        val validCategoryIds = categories.map { it.id }.toSet()

        val reminders = payload.reminders.map {
            Reminder(
                id = it.id,
                title = it.title.trim(),
                description = it.description,
                scheduledTimeMillis = it.scheduledTimeMillis,
                additionalTimesMillis = it.additionalTimesMillis.filter { t -> t > 0L },
                repeatType = RepeatType.fromString(it.repeatType),
                customDaysMask = it.customDaysMask,
                categoryId = if (it.categoryId in validCategoryIds) it.categoryId else (categories.firstOrNull()?.id ?: 1L),
                priority = Priority.fromString(it.priority),
                soundUri = it.soundUri.ifBlank { "builtin:classic_alarm" },
                soundTitle = it.soundTitle.ifBlank { "Classic Alarm" },
                vibrationEnabled = it.vibrationEnabled,
                snoozeDurationMinutes = it.snoozeDurationMinutes.coerceIn(1, 60),
                isEnabled = it.isEnabled,
                isCompleted = it.isCompleted,
                snoozedUntilMillis = it.snoozedUntilMillis,
                completedAtMillis = it.completedAtMillis,
                createdAtMillis = it.createdAtMillis,
                updatedAtMillis = it.updatedAtMillis
            )
        }

        val prefs = AppPreferences(
            themeMode = ThemeMode.fromString(payload.preferences.themeMode),
            defaultSoundUri = payload.preferences.defaultSoundUri.ifBlank { "builtin:classic_alarm" },
            defaultSoundTitle = payload.preferences.defaultSoundTitle.ifBlank { "Classic Alarm" },
            defaultAlarmVolume = payload.preferences.defaultAlarmVolume.coerceIn(0.1f, 1.0f),
            defaultSnoozeMinutes = payload.preferences.defaultSnoozeMinutes.coerceIn(1, 60),
            defaultVibrationEnabled = payload.preferences.defaultVibrationEnabled,
            gradualVolumeIncrease = payload.preferences.gradualVolumeIncrease,
            snoozePlaySound = payload.preferences.snoozePlaySound,
            use24HourFormat = payload.preferences.use24HourFormat,
            firstDayOfWeek = if (payload.preferences.firstDayOfWeek in 1..7) payload.preferences.firstDayOfWeek else 1
        )

        return BackupParseResult.Valid(categories, reminders, prefs)
    }
}
