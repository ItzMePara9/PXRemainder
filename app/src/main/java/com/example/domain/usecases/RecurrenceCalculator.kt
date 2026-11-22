package com.example.domain.usecases

import com.example.domain.models.Reminder
import com.example.domain.models.RepeatType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.min

/**
 * Computes recurring alarm trigger timestamps and calendar projections
 * accurately across DST transitions, month-end clamping, and reboots.
 */
object RecurrenceCalculator {

    /**
     * Bitmask convention: Monday = bit 0 (1), Tuesday = bit 1 (2), ..., Sunday = bit 6 (64).
     */
    fun dayBit(dayOfWeek: DayOfWeek): Int = 1 shl (dayOfWeek.value - 1)

    fun isDaySelected(mask: Int, dayOfWeek: DayOfWeek): Boolean =
        (mask and dayBit(dayOfWeek)) != 0

    fun toggleDay(mask: Int, dayOfWeek: DayOfWeek): Int =
        mask xor dayBit(dayOfWeek)

    fun formatCustomDays(mask: Int): String {
        if (mask == 0) return "No days"
        val labels = listOf(
            DayOfWeek.MONDAY to "Mon",
            DayOfWeek.TUESDAY to "Tue",
            DayOfWeek.WEDNESDAY to "Wed",
            DayOfWeek.THURSDAY to "Thu",
            DayOfWeek.FRIDAY to "Fri",
            DayOfWeek.SATURDAY to "Sat",
            DayOfWeek.SUNDAY to "Sun"
        )
        return labels.filter { isDaySelected(mask, it.first) }.joinToString(", ") { it.second }
    }

    /**
     * Given a scheduled trigger timestamp and recurrence rule, computes the next occurrence
     * strictly after [afterMillis]. Returns null if [repeatType] is [RepeatType.NEVER].
     */
    fun nextOccurrenceAfter(
        baseMillis: Long,
        repeatType: RepeatType,
        customDaysMask: Int,
        afterMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (repeatType == RepeatType.NEVER) return null

        val baseZdt = Instant.ofEpochMilli(baseMillis).atZone(zoneId)
        val localTime = baseZdt.toLocalTime()
        var candidateDate = baseZdt.toLocalDate()
        val targetInstant = Instant.ofEpochMilli(afterMillis)

        // If base is already in the future relative to afterMillis and matches rule, return it or step forward
        when (repeatType) {
            RepeatType.DAILY -> {
                var candidate = ZonedDateTime.of(candidateDate, localTime, zoneId)
                while (!candidate.toInstant().isAfter(targetInstant)) {
                    candidateDate = candidateDate.plusDays(1)
                    candidate = ZonedDateTime.of(candidateDate, localTime, zoneId)
                }
                return candidate.toInstant().toEpochMilli()
            }

            RepeatType.WEEKDAYS -> {
                var candidate = ZonedDateTime.of(candidateDate, localTime, zoneId)
                while (!candidate.toInstant().isAfter(targetInstant) ||
                    candidate.dayOfWeek == DayOfWeek.SATURDAY ||
                    candidate.dayOfWeek == DayOfWeek.SUNDAY
                ) {
                    candidateDate = candidateDate.plusDays(1)
                    candidate = ZonedDateTime.of(candidateDate, localTime, zoneId)
                }
                return candidate.toInstant().toEpochMilli()
            }

            RepeatType.WEEKLY -> {
                var candidate = ZonedDateTime.of(candidateDate, localTime, zoneId)
                while (!candidate.toInstant().isAfter(targetInstant)) {
                    candidateDate = candidateDate.plusWeeks(1)
                    candidate = ZonedDateTime.of(candidateDate, localTime, zoneId)
                }
                return candidate.toInstant().toEpochMilli()
            }

            RepeatType.MONTHLY -> {
                val anchorDay = candidateDate.dayOfMonth
                var ym = YearMonth.from(candidateDate)
                var candidate = zonedWithClampedDay(ym, anchorDay, localTime, zoneId)
                while (!candidate.toInstant().isAfter(targetInstant)) {
                    ym = ym.plusMonths(1)
                    candidate = zonedWithClampedDay(ym, anchorDay, localTime, zoneId)
                }
                return candidate.toInstant().toEpochMilli()
            }

            RepeatType.CUSTOM_DAYS -> {
                val effectiveMask = if (customDaysMask == 0) dayBit(baseZdt.dayOfWeek) else customDaysMask
                var candidate = ZonedDateTime.of(candidateDate, localTime, zoneId)
                var guard = 0
                while ((!candidate.toInstant().isAfter(targetInstant) || !isDaySelected(effectiveMask, candidate.dayOfWeek)) && guard < 400) {
                    candidateDate = candidateDate.plusDays(1)
                    candidate = ZonedDateTime.of(candidateDate, localTime, zoneId)
                    guard++
                }
                return candidate.toInstant().toEpochMilli()
            }

            RepeatType.NEVER -> return null
        }
    }

    private fun zonedWithClampedDay(
        ym: YearMonth,
        anchorDay: Int,
        time: LocalTime,
        zoneId: ZoneId
    ): ZonedDateTime {
        val day = min(anchorDay, ym.lengthOfMonth())
        return ZonedDateTime.of(ym.atDay(day), time, zoneId)
    }

    /**
     * Reconstructs a valid future trigger timestamp on boot, timezone change, or clock change.
     * For non-recurring alarms in the past (within a 24h grace window for recent missed/boot),
     * returns null if already expired or keeps future ones intact.
     */
    fun reconstructNextTrigger(
        reminder: Reminder,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Reminder {
        if (!reminder.isEnabled || reminder.isCompleted) return reminder

        val updatedPrimary = if (reminder.scheduledTimeMillis <= nowMillis) {
            if (reminder.repeatType != RepeatType.NEVER) {
                nextOccurrenceAfter(
                    baseMillis = reminder.scheduledTimeMillis,
                    repeatType = reminder.repeatType,
                    customDaysMask = reminder.customDaysMask,
                    afterMillis = nowMillis,
                    zoneId = zoneId
                ) ?: reminder.scheduledTimeMillis
            } else {
                reminder.scheduledTimeMillis
            }
        } else {
            reminder.scheduledTimeMillis
        }

        val updatedExtras = reminder.additionalTimesMillis.mapNotNull { slotMillis ->
            if (slotMillis > nowMillis) {
                slotMillis
            } else if (reminder.repeatType != RepeatType.NEVER) {
                nextOccurrenceAfter(
                    baseMillis = slotMillis,
                    repeatType = reminder.repeatType,
                    customDaysMask = reminder.customDaysMask,
                    afterMillis = nowMillis,
                    zoneId = zoneId
                )
            } else {
                null
            }
        }

        val updatedSnooze = reminder.snoozedUntilMillis?.takeIf { it > nowMillis }

        return reminder.copy(
            scheduledTimeMillis = updatedPrimary,
            additionalTimesMillis = updatedExtras,
            snoozedUntilMillis = updatedSnooze
        )
    }

    /**
     * Determines if [reminder] has at least one scheduled or recurring occurrence on [date].
     */
    fun occursOnDate(
        reminder: Reminder,
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val allBaseTimes = buildList {
            add(reminder.scheduledTimeMillis)
            addAll(reminder.additionalTimesMillis)
        }

        return allBaseTimes.any { millis ->
            val startDate = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
            if (date.isBefore(startDate)) return@any false

            when (reminder.repeatType) {
                RepeatType.NEVER -> startDate == date
                RepeatType.DAILY -> true
                RepeatType.WEEKDAYS -> date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
                RepeatType.WEEKLY -> date.dayOfWeek == startDate.dayOfWeek
                RepeatType.MONTHLY -> {
                    val targetDay = min(startDate.dayOfMonth, YearMonth.from(date).lengthOfMonth())
                    date.dayOfMonth == targetDay
                }
                RepeatType.CUSTOM_DAYS -> {
                    val mask = if (reminder.customDaysMask == 0) dayBit(startDate.dayOfWeek) else reminder.customDaysMask
                    isDaySelected(mask, date.dayOfWeek)
                }
            }
        }
    }
}
