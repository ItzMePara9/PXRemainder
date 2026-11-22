package com.example

import com.example.domain.models.AppPreferences
import com.example.domain.models.Category
import com.example.domain.models.Priority
import com.example.domain.models.Reminder
import com.example.domain.models.RepeatType
import com.example.domain.models.ThemeMode
import com.example.utils.BackupManager
import com.example.utils.BackupParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidationTest {

    @Test
    fun `export and import roundtrip succeeds`() {
        val categories = listOf(
            Category(id = 1L, name = "Personal", iconKey = "person", colorHex = 0xFFA78BFA, isDefault = true)
        )
        val reminders = listOf(
            Reminder(
                id = 10L,
                title = "Standup",
                description = "Daily team sync",
                scheduledTimeMillis = 1800000000000L,
                additionalTimesMillis = listOf(1800003600000L),
                repeatType = RepeatType.WEEKDAYS,
                categoryId = 1L,
                priority = Priority.HIGH,
                snoozeDurationMinutes = 5
            )
        )
        val prefs = AppPreferences(
            themeMode = ThemeMode.DARK,
            use24HourFormat = true,
            defaultSnoozeMinutes = 15
        )

        val json = BackupManager.exportToJson(categories, reminders, prefs)
        val result = BackupManager.parseAndValidate(json)
        assertTrue(result is BackupParseResult.Valid)
        val valid = result as BackupParseResult.Valid
        assertEquals(1, valid.reminders.size)
        assertEquals("Standup", valid.reminders.first().title)
        assertEquals(RepeatType.WEEKDAYS, valid.reminders.first().repeatType)
        assertTrue(valid.preferences.use24HourFormat)
    }

    @Test
    fun `invalid json is rejected cleanly`() {
        val result = BackupManager.parseAndValidate("{ \"not\": \"valid\" }")
        assertTrue(result is BackupParseResult.Invalid)
    }
}
