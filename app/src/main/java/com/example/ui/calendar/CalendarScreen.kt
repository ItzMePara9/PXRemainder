package com.example.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.models.Reminder
import com.example.domain.usecases.TimeFormatters
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.ReminderRowItem
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToToday: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onCreateForDate: (LocalDate) -> Unit,
    onEditReminder: (Long) -> Unit,
    onToggleEnabled: (Reminder, Boolean) -> Unit,
    onToggleCompleted: (Reminder) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val firstDaySetting = DayOfWeek.of(uiState.preferences.firstDayOfWeek.coerceIn(1, 7))
    val orderedWeekDays = (0 until 7).map { offset ->
        DayOfWeek.of(((firstDaySetting.value - 1 + offset) % 7) + 1)
    }

    val monthStart = uiState.displayedMonth.atDay(1)
    val leadingEmptyCount = (monthStart.dayOfWeek.value - firstDaySetting.value + 7) % 7
    val totalDays = uiState.displayedMonth.lengthOfMonth()
    val totalCells = leadingEmptyCount + totalDays
    val rowCount = (totalCells + 6) / 7

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "calendar_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CALENDAR",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = TimeFormatters.formatMonthYear(monthStart),
                            fontFamily = SpaceGroteskFontFamily,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onJumpToToday) {
                            Text("Today", style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(
                            onClick = onPreviousMonth,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("calendar_prev_month")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "Previous month"
                            )
                        }
                        IconButton(
                            onClick = onNextMonth,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("calendar_next_month")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Next month"
                            )
                        }
                    }
                }
            }

            item(key = "month_grid") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Weekday header row
                        Row(modifier = Modifier.fillMaxWidth()) {
                            orderedWeekDays.forEach { dow ->
                                Text(
                                    text = dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        for (row in 0 until rowCount) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                for (col in 0 until 7) {
                                    val cellIndex = row * 7 + col
                                    val dayNumber = cellIndex - leadingEmptyCount + 1
                                    if (dayNumber in 1..totalDays) {
                                        val date = uiState.displayedMonth.atDay(dayNumber)
                                        val isSelected = date == uiState.selectedDate
                                        val isToday = date == today
                                        val hasReminder = date in uiState.datesWithReminders

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .padding(2.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .clickable { onSelectDate(date) }
                                                .testTag("calendar_day_$dayNumber"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = dayNumber.toString(),
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                    color = when {
                                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                                        isToday -> MaterialTheme.colorScheme.primary
                                                        else -> MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            when {
                                                                !hasReminder -> Color.Transparent
                                                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                                                else -> MaterialTheme.colorScheme.primary
                                                            }
                                                        )
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "selected_date_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = TimeFormatters.formatFullDate(uiState.selectedDate),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${uiState.selectedDateReminders.size} reminder(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { onCreateForDate(uiState.selectedDate) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("calendar_add_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Schedule", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (uiState.selectedDateReminders.isEmpty()) {
                item(key = "calendar_empty") {
                    EmptyStatePanel(
                        title = "No reminders on this date",
                        subtitle = "Tap Schedule to create an alarm for ${TimeFormatters.formatFullDate(uiState.selectedDate)}."
                    )
                }
            } else {
                items(uiState.selectedDateReminders, key = { "cal_${it.id}" }) { reminder ->
                    ReminderRowItem(
                        reminder = reminder,
                        category = uiState.categoriesById[reminder.categoryId],
                        use24Hour = uiState.preferences.use24HourFormat,
                        onClick = { onEditReminder(reminder.id) },
                        onToggleEnabled = { onToggleEnabled(reminder, it) },
                        onToggleCompleted = { onToggleCompleted(reminder) }
                    )
                }
            }
        }
    }
}
