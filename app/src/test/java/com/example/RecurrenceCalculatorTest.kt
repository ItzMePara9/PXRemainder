package com.example

import com.example.domain.models.Reminder
import com.example.domain.models.RepeatType
import com.example.domain.usecases.RecurrenceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class RecurrenceCalculatorTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun `never repeat returns null`() {
        val base = LocalDateTime.of(2026, 11, 21, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertNull(
            RecurrenceCalculator.nextOccurrenceAfter(base, RepeatType.NEVER, 0, base + 1000L, zone)
        )
    }

    @Test
    fun `daily recurrence advances by one day`() {
        val base = LocalDateTime.of(2026, 11, 21, 8, 30).atZone(zone).toInstant().toEpochMilli()
        val next = RecurrenceCalculator.nextOccurrenceAfter(base, RepeatType.DAILY, 0, base, zone)!!
        val nextDate = Instant.ofEpochMilli(next).atZone(zone).toLocalDateTime()
        assertEquals(LocalDateTime.of(2026, 11, 22, 8, 30), nextDate)
    }

    @Test
    fun `weekdays recurrence skips saturday and sunday`() {
        // 2026-11-20 is Friday
        val friday = LocalDateTime.of(2026, 11, 20, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val next = RecurrenceCalculator.nextOccurrenceAfter(friday, RepeatType.WEEKDAYS, 0, friday, zone)!!
        val nextDate = Instant.ofEpochMilli(next).atZone(zone).toLocalDate()
        assertEquals(DayOfWeek.MONDAY, nextDate.dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 23), nextDate)
    }

    @Test
    fun `monthly recurrence clamps 31st to 28th in february`() {
        val jan31 = LocalDateTime.of(2026, 1, 31, 10, 0).atZone(zone).toInstant().toEpochMilli()
        val next = RecurrenceCalculator.nextOccurrenceAfter(jan31, RepeatType.MONTHLY, 0, jan31, zone)!!
        val nextDate = Instant.ofEpochMilli(next).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 2, 28), nextDate)
    }

    @Test
    fun `custom days recurrence selects matching days on calendar`() {
        val start = LocalDateTime.of(2026, 11, 16, 7, 0).atZone(zone).toInstant().toEpochMilli() // Monday
        val mask = RecurrenceCalculator.dayBit(DayOfWeek.MONDAY) or RecurrenceCalculator.dayBit(DayOfWeek.WEDNESDAY)
        val reminder = Reminder(
            id = 1L,
            title = "Workout",
            scheduledTimeMillis = start,
            repeatType = RepeatType.CUSTOM_DAYS,
            customDaysMask = mask
        )
        assertTrue(RecurrenceCalculator.occursOnDate(reminder, LocalDate.of(2026, 11, 16), zone))
        assertFalse(RecurrenceCalculator.occursOnDate(reminder, LocalDate.of(2026, 11, 17), zone))
        assertTrue(RecurrenceCalculator.occursOnDate(reminder, LocalDate.of(2026, 11, 18), zone))
    }
}
