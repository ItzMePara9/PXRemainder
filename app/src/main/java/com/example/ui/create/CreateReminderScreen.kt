package com.example.ui.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.models.Category
import com.example.domain.models.Priority
import com.example.domain.models.RepeatType
import com.example.domain.usecases.RecurrenceCalculator
import com.example.domain.usecases.TimeFormatters
import com.example.ui.components.CategoryEditorDialog
import com.example.ui.components.SectionHeaderLabel
import com.example.ui.components.SoundPickerSheet
import com.example.ui.components.priorityColor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.utils.CategoryIconCatalog
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateReminderScreen(
    uiState: CreateReminderUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPrimaryDateChange: (LocalDate) -> Unit,
    onPrimaryTimeChange: (LocalTime) -> Unit,
    onAddScheduleSlot: (LocalDate, LocalTime) -> Unit,
    onRemoveScheduleSlot: (Int) -> Unit,
    onRepeatTypeChange: (RepeatType) -> Unit,
    onToggleCustomDay: (DayOfWeek) -> Unit,
    onCategoryChange: (Long) -> Unit,
    onSaveCustomCategory: (Category) -> Unit,
    onPriorityChange: (Priority) -> Unit,
    onSoundChange: (String, String) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onSnoozeDurationChange: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var showPrimaryDatePicker by remember { mutableStateOf(false) }
    var showPrimaryTimePicker by remember { mutableStateOf(false) }
    var showExtraSlotDatePicker by remember { mutableStateOf(false) }
    var showExtraSlotTimePicker by remember { mutableStateOf(false) }
    var pendingExtraDate by remember { mutableStateOf(uiState.primaryDate) }
    var showSoundSheet by remember { mutableStateOf(false) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val selectedCategory = uiState.categories.firstOrNull { it.id == uiState.categoryId }
    val primaryMillis = remember(uiState.primaryDate, uiState.primaryTime) {
        ScheduleSlotUi(uiState.primaryDate, uiState.primaryTime).toEpochMillis()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Text(
                        text = if (uiState.isEditing) "Edit Reminder" else "New Reminder",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("create_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.isEditing) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("delete_reminder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete reminder",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title & Description
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = onTitleChange,
                        label = { Text("Reminder title") },
                        placeholder = { Text("e.g., Morning medication or System standup") },
                        isError = uiState.titleError != null,
                        supportingText = uiState.titleError?.let { err -> { Text(err) } },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reminder_title_input")
                    )

                    OutlinedTextField(
                        value = uiState.description,
                        onValueChange = onDescriptionChange,
                        label = { Text("Notes / Description (optional)") },
                        placeholder = { Text("Context shown on the full-screen alarm…") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reminder_description_input")
                    )
                }

                // Schedule Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeaderLabel(title = "Schedule")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showPrimaryDatePicker = true }
                                .testTag("pick_date_button"),
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarToday,
                                    contentDescription = "Select date",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Date",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = TimeFormatters.formatDateRelative(primaryMillis),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showPrimaryTimePicker = true }
                                .testTag("pick_time_button"),
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = "Select time",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Time",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = TimeFormatters.formatLocalTime(
                                            uiState.primaryTime,
                                            uiState.preferences.use24HourFormat
                                        ),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Additional Schedules
                    if (uiState.additionalSlots.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            uiState.additionalSlots.forEachIndexed { index, slot ->
                                val slotMillis = slot.toEpochMillis()
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Extra #${index + 1}: ${TimeFormatters.formatDateRelative(slotMillis)} • ${TimeFormatters.formatLocalTime(slot.time, uiState.preferences.use24HourFormat)}",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        IconButton(
                                            onClick = { onRemoveScheduleSlot(index) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Remove schedule",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            pendingExtraDate = uiState.primaryDate
                            showExtraSlotDatePicker = true
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("add_extra_schedule_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add another schedule", style = MaterialTheme.typography.labelLarge)
                    }

                    if (uiState.scheduleError != null) {
                        Text(
                            text = uiState.scheduleError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("schedule_error_text")
                        )
                    }
                }

                // Repeat Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeaderLabel(title = "Repeat")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RepeatType.entries.forEach { type ->
                            val selected = uiState.repeatType == type
                            SelectableChip(
                                label = type.label,
                                selected = selected,
                                onClick = { onRepeatTypeChange(type) },
                                modifier = Modifier.testTag("repeat_${type.name.lowercase()}")
                            )
                        }
                    }

                    if (uiState.repeatType == RepeatType.CUSTOM_DAYS) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val days = listOf(
                                DayOfWeek.MONDAY to "M",
                                DayOfWeek.TUESDAY to "T",
                                DayOfWeek.WEDNESDAY to "W",
                                DayOfWeek.THURSDAY to "T",
                                DayOfWeek.FRIDAY to "F",
                                DayOfWeek.SATURDAY to "S",
                                DayOfWeek.SUNDAY to "S"
                            )
                            days.forEach { (dow, shortLabel) ->
                                val selected = RecurrenceCalculator.isDaySelected(uiState.customDaysMask, dow)
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .clickable { onToggleCustomDay(dow) }
                                        .testTag("custom_day_${dow.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = shortLabel,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Category Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CATEGORY",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showNewCategoryDialog = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Custom", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.categories.forEach { category ->
                            val selected = uiState.categoryId == category.id
                            val catColor = Color(category.colorHex)
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onCategoryChange(category.id) },
                                color = if (selected) catColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) catColor else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = CategoryIconCatalog.iconForKey(category.iconKey),
                                        contentDescription = null,
                                        tint = catColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) catColor else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Priority Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeaderLabel(title = "Priority")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Priority.entries.forEach { priority ->
                            val selected = uiState.priority == priority
                            val pColor = priorityColor(priority)
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onPriorityChange(priority) },
                                color = if (selected) pColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) pColor else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(pColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = priority.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) pColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Alarm Sound, Vibration & Snooze
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeaderLabel(title = "Alarm Behavior")

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showSoundSheet = true }
                            .testTag("select_sound_button"),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Alarm Ringtone",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = uiState.soundTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Text(
                                text = "Preview & Change",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Vibration,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Vibrate on alarm",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Switch(
                                checked = uiState.vibrationEnabled,
                                onCheckedChange = onVibrationChange,
                                modifier = Modifier.testTag("vibration_switch")
                            )
                        }
                    }

                    Text(
                        text = "Snooze Interval",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 10, 15, 20, 30).forEach { mins ->
                            val selected = uiState.snoozeDurationMinutes == mins
                            SelectableChip(
                                label = "${mins}m",
                                selected = selected,
                                onClick = { onSnoozeDurationChange(mins) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Summary Preview Card before saving
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "SUMMARY",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = uiState.title.ifBlank { "Untitled Reminder" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val scheduleCount = 1 + uiState.additionalSlots.size
                        Text(
                            text = "${TimeFormatters.formatDateRelative(primaryMillis)} at ${TimeFormatters.formatLocalTime(uiState.primaryTime, uiState.preferences.use24HourFormat)} • ${uiState.repeatType.label}" +
                                if (scheduleCount > 1) " ($scheduleCount schedules)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${selectedCategory?.name ?: "Personal"} • ${uiState.priority.label} priority • ${uiState.soundTitle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onSave,
                    enabled = !uiState.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_reminder_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (uiState.isEditing) "Save Changes" else "Schedule Alarm",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showPrimaryDatePicker) {
        val initialMillis = uiState.primaryDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPrimaryDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onPrimaryDateChange(picked)
                    }
                    showPrimaryDatePicker = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showPrimaryDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showPrimaryTimePicker) {
        val state = rememberTimePickerState(
            initialHour = uiState.primaryTime.hour,
            initialMinute = uiState.primaryTime.minute,
            is24Hour = uiState.preferences.use24HourFormat
        )
        AlertDialog(
            onDismissRequest = { showPrimaryTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onPrimaryTimeChange(LocalTime.of(state.hour, state.minute))
                    showPrimaryTimePicker = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showPrimaryTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) }
        )
    }

    if (showExtraSlotDatePicker) {
        val initialMillis = pendingExtraDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showExtraSlotDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        pendingExtraDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showExtraSlotDatePicker = false
                    showExtraSlotTimePicker = true
                }) { Text("Next: Time") }
            },
            dismissButton = {
                TextButton(onClick = { showExtraSlotDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showExtraSlotTimePicker) {
        val state = rememberTimePickerState(
            initialHour = uiState.primaryTime.hour,
            initialMinute = uiState.primaryTime.minute,
            is24Hour = uiState.preferences.use24HourFormat
        )
        AlertDialog(
            onDismissRequest = { showExtraSlotTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onAddScheduleSlot(pendingExtraDate, LocalTime.of(state.hour, state.minute))
                    showExtraSlotTimePicker = false
                }) { Text("Add Slot") }
            },
            dismissButton = {
                TextButton(onClick = { showExtraSlotTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) }
        )
    }

    if (showSoundSheet) {
        SoundPickerSheet(
            selectedSoundUri = uiState.soundUri,
            selectedSoundTitle = uiState.soundTitle,
            previewVolume = uiState.preferences.defaultAlarmVolume,
            onSelectSound = onSoundChange,
            onDismiss = { showSoundSheet = false }
        )
    }

    if (showNewCategoryDialog) {
        CategoryEditorDialog(
            initial = Category(
                name = "",
                iconKey = "label",
                colorHex = CategoryIconCatalog.colorPalette.first(),
                isDefault = false
            ),
            onSave = { cat ->
                onSaveCustomCategory(cat)
                showNewCategoryDialog = false
            },
            onDismiss = { showNewCategoryDialog = false }
        )
    }

    if (showDeleteConfirm) {
        val isImportant = uiState.priority == Priority.HIGH ||
            uiState.priority == Priority.URGENT ||
            uiState.repeatType != RepeatType.NEVER
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(if (isImportant) "Delete Important Reminder?" else "Delete Reminder?")
            },
            text = {
                Text(
                    if (isImportant) {
                        "\"${uiState.title}\" is marked ${uiState.priority.label}${if (uiState.repeatType != RepeatType.NEVER) " and repeats (${uiState.repeatType.label})" else ""}. Deleting it will cancel all scheduled alarms."
                    } else {
                        "Are you sure you want to permanently delete \"${uiState.title}\"?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Keep")
                }
            }
        )
    }
}

@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
