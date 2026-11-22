package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.MainActivity
import com.example.domain.models.ActiveAlarm
import com.example.domain.models.Category
import com.example.domain.models.Reminder
import com.example.domain.usecases.RecurrenceCalculator
import com.example.utils.PermissionHelper
import kotlin.math.max

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleReminder(reminder: Reminder, category: Category? = null): Boolean {
        cancelReminder(reminder.id)
        if (!reminder.isEnabled || reminder.isCompleted) return false

        val now = System.currentTimeMillis()
        var scheduledAny = false

        // Slot 0: Primary schedule or active snooze
        val primaryTarget = reminder.snoozedUntilMillis ?: reminder.scheduledTimeMillis
        val isSnooze = reminder.snoozedUntilMillis != null
        if (primaryTarget > now - 30_000L) {
            val safeTarget = max(primaryTarget, now + 500L)
            scheduleExactOrFallback(
                reminderId = reminder.id,
                slotIndex = 0,
                triggerAtMillis = safeTarget,
                isSnooze = isSnooze,
                title = reminder.title,
                description = reminder.description,
                scheduledTimeMillis = primaryTarget,
                categoryName = category?.name ?: "Reminder",
                categoryColorHex = category?.colorHex ?: 0xFFA78BFA,
                soundUri = reminder.soundUri,
                vibrationEnabled = reminder.vibrationEnabled,
                snoozeDurationMinutes = reminder.snoozeDurationMinutes
            )
            scheduledAny = true
        }

        // Slots 1..N: Additional configured schedules
        reminder.additionalTimesMillis.take(MAX_SLOTS_PER_REMINDER - 1).forEachIndexed { index, timeMillis ->
            val slotIndex = index + 1
            if (timeMillis > now - 30_000L) {
                val safeTarget = max(timeMillis, now + 500L)
                scheduleExactOrFallback(
                    reminderId = reminder.id,
                    slotIndex = slotIndex,
                    triggerAtMillis = safeTarget,
                    isSnooze = false,
                    title = reminder.title,
                    description = reminder.description,
                    scheduledTimeMillis = timeMillis,
                    categoryName = category?.name ?: "Reminder",
                    categoryColorHex = category?.colorHex ?: 0xFFA78BFA,
                    soundUri = reminder.soundUri,
                    vibrationEnabled = reminder.vibrationEnabled,
                    snoozeDurationMinutes = reminder.snoozeDurationMinutes
                )
                scheduledAny = true
            }
        }

        return scheduledAny
    }

    fun scheduleSnooze(
        reminderId: Long,
        slotIndex: Int,
        snoozeMinutes: Int,
        reminder: Reminder? = null,
        category: Category? = null,
        fallbackAlarm: ActiveAlarm? = null
    ): Long {
        val triggerAt = System.currentTimeMillis() + snoozeMinutes.coerceIn(1, 60) * 60_000L
        scheduleExactOrFallback(
            reminderId = reminderId,
            slotIndex = slotIndex,
            triggerAtMillis = triggerAt,
            isSnooze = true,
            title = reminder?.title ?: fallbackAlarm?.title ?: "Reminder",
            description = reminder?.description ?: fallbackAlarm?.description ?: "",
            scheduledTimeMillis = triggerAt,
            categoryName = category?.name ?: fallbackAlarm?.categoryName ?: "Reminder",
            categoryColorHex = category?.colorHex ?: fallbackAlarm?.categoryColorHex ?: 0xFFA78BFA,
            soundUri = reminder?.soundUri ?: fallbackAlarm?.soundUri ?: "builtin:classic_alarm",
            vibrationEnabled = reminder?.vibrationEnabled ?: fallbackAlarm?.vibrationEnabled ?: true,
            snoozeDurationMinutes = snoozeMinutes
        )
        return triggerAt
    }

    fun cancelReminder(reminderId: Long) {
        for (slotIndex in 0 until MAX_SLOTS_PER_REMINDER) {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_ALARM_TRIGGER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                computeRequestCode(reminderId, slotIndex),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    fun rescheduleAll(
        reminders: List<Reminder>,
        categoriesById: Map<Long, Category> = emptyMap()
    ): List<Reminder> {
        val now = System.currentTimeMillis()
        val updatedList = mutableListOf<Reminder>()
        reminders.forEach { reminder ->
            val reconstructed = RecurrenceCalculator.reconstructNextTrigger(reminder, now)
            updatedList.add(reconstructed)
            if (reconstructed.isEnabled && !reconstructed.isCompleted) {
                scheduleReminder(reconstructed, categoriesById[reconstructed.categoryId])
            } else {
                cancelReminder(reconstructed.id)
            }
        }
        return updatedList
    }

    private fun scheduleExactOrFallback(
        reminderId: Long,
        slotIndex: Int,
        triggerAtMillis: Long,
        isSnooze: Boolean,
        title: String,
        description: String,
        scheduledTimeMillis: Long,
        categoryName: String,
        categoryColorHex: Long,
        soundUri: String,
        vibrationEnabled: Boolean,
        snoozeDurationMinutes: Int
    ) {
        val triggerIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_SLOT_INDEX, slotIndex)
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_DESCRIPTION, description)
            putExtra(EXTRA_SCHEDULED_MILLIS, scheduledTimeMillis)
            putExtra(EXTRA_CATEGORY_NAME, categoryName)
            putExtra(EXTRA_CATEGORY_COLOR, categoryColorHex)
            putExtra(EXTRA_SOUND_URI, soundUri)
            putExtra(EXTRA_VIBRATION_ENABLED, vibrationEnabled)
            putExtra(EXTRA_SNOOZE_MINUTES, snoozeDurationMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            computeRequestCode(reminderId, slotIndex),
            triggerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (PermissionHelper.canScheduleExactAlarms(context)) {
            val showIntent = PendingIntent.getActivity(
                context,
                computeRequestCode(reminderId, slotIndex) + 10000,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    companion object {
        const val ACTION_ALARM_TRIGGER = "com.example.action.ALARM_TRIGGER"
        const val ACTION_ALARM_SNOOZE = "com.example.action.ALARM_SNOOZE"
        const val ACTION_ALARM_DISMISS = "com.example.action.ALARM_DISMISS"

        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_SLOT_INDEX = "extra_slot_index"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_SCHEDULED_MILLIS = "extra_scheduled_millis"
        const val EXTRA_CATEGORY_NAME = "extra_category_name"
        const val EXTRA_CATEGORY_COLOR = "extra_category_color"
        const val EXTRA_SOUND_URI = "extra_sound_uri"
        const val EXTRA_VIBRATION_ENABLED = "extra_vibration_enabled"

        const val MAX_SLOTS_PER_REMINDER = 12

        fun computeRequestCode(reminderId: Long, slotIndex: Int): Int {
            val base = (reminderId and 0x7FFFFFL).toInt()
            return base * 20 + slotIndex
        }
    }
}
