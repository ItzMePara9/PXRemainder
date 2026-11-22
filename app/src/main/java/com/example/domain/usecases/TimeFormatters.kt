package com.example.domain.usecases

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object TimeFormatters {

    private val time24Formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val time12Formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val dateShortFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val dateFullFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    fun formatTime(
        epochMillis: Long,
        use24Hour: Boolean,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val zdt = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        return zdt.format(if (use24Hour) time24Formatter else time12Formatter)
    }

    fun formatLocalTime(localTime: LocalTime, use24Hour: Boolean): String =
        localTime.format(if (use24Hour) time24Formatter else time12Formatter)

    fun formatDateRelative(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowDate: LocalDate = LocalDate.now(zoneId)
    ): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
        return when (date) {
            nowDate -> "Today"
            nowDate.plusDays(1) -> "Tomorrow"
            nowDate.minusDays(1) -> "Yesterday"
            else -> date.format(dateShortFormatter)
        }
    }

    fun formatFullDate(date: LocalDate): String = date.format(dateFullFormatter)

    fun formatMonthYear(date: LocalDate): String = date.format(monthYearFormatter)

    fun formatCountdown(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diff = targetMillis - nowMillis
        if (diff <= 0L) return "Due now"
        val totalMinutes = (diff + 59_999L) / 60_000L
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> "in ${days}d ${hours}h"
            hours > 0 && minutes > 0 -> "in ${hours}h ${minutes}m"
            hours > 0 -> "in ${hours}h"
            else -> "in ${minutes}m"
        }
    }

    fun formatGreeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Late night focus"
    }

    fun formatDurationMinutes(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60}h ${abs(minutes % 60)}m"
    }
}
