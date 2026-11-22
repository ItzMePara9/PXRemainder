package com.example.ui.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.domain.models.Category
import com.example.domain.models.Reminder
import com.example.domain.models.ReminderFilter
import com.example.domain.usecases.TimeFormatters
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.PermissionStatusBanner
import com.example.ui.components.ReminderRowItem
import com.example.ui.components.SectionHeaderLabel
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.utils.CategoryIconCatalog
import com.example.utils.PermissionHelper
import java.time.LocalDate

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onRefreshPermissions: () -> Unit,
    onSelectFilter: (ReminderFilter) -> Unit,
    onSelectCategory: (Long?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCreateReminder: () -> Unit,
    onEditReminder: (Long) -> Unit,
    onToggleEnabled: (Reminder, Boolean) -> Unit,
    onToggleCompleted: (Reminder) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshPermissions()
    }

    val todayFormatted = remember { TimeFormatters.formatFullDate(LocalDate.now()) }
    val greeting = remember { TimeFormatters.formatGreeting() }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Branding + Date + Greeting
            item(key = "header") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PXREMINDER",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = if (uiState.permissionHealth.exactAlarmGranted) {
                                "EXACT ENGINE"
                            } else {
                                "APPROX MODE"
                            },
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.permissionHealth.exactAlarmGranted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color(0xFFFBBF24)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = greeting,
                        fontFamily = SpaceGroteskFontFamily,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = todayFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Permission Banner (if needed)
            if (!uiState.permissionHealth.allGranted) {
                item(key = "permissions") {
                    PermissionStatusBanner(
                        health = uiState.permissionHealth,
                        onRequestNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivity(PermissionHelper.notificationSettingsIntent(context))
                            }
                        },
                        onOpenExactAlarmSettings = {
                            context.startActivity(PermissionHelper.exactAlarmSettingsIntent(context))
                        },
                        onOpenFullScreenSettings = {
                            context.startActivity(PermissionHelper.fullScreenIntentSettingsIntent(context))
                        }
                    )
                }
            }

            // Next Upcoming Reminder Hero Row
            uiState.nextUpcoming?.let { nextReminder ->
                item(key = "next_upcoming") {
                    NextUpcomingPanel(
                        reminder = nextReminder,
                        category = uiState.categoriesById[nextReminder.categoryId],
                        use24Hour = uiState.preferences.use24HourFormat,
                        onClick = { onEditReminder(nextReminder.id) }
                    )
                }
            }

            // Search Bar
            item(key = "search") {
                SearchInputBar(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChange
                )
            }

            // Filter Pills + Category Filter Row
            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReminderFilter.entries.forEach { filter ->
                            val selected = uiState.selectedFilter == filter
                            FilterChipPill(
                                label = filter.label,
                                selected = selected,
                                onClick = { onSelectFilter(filter) },
                                modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChipPill(
                            label = "All Categories",
                            selected = uiState.selectedCategoryId == null,
                            onClick = { onSelectCategory(null) },
                            compact = true
                        )
                        uiState.categories.forEach { category ->
                            CategoryFilterPill(
                                category = category,
                                selected = uiState.selectedCategoryId == category.id,
                                onClick = { onSelectCategory(category.id) }
                            )
                        }
                    }
                }
            }

            // Reminder Sections based on active filter
            val showToday = uiState.selectedFilter == ReminderFilter.ALL || uiState.selectedFilter == ReminderFilter.TODAY
            val showUpcoming = uiState.selectedFilter == ReminderFilter.ALL || uiState.selectedFilter == ReminderFilter.UPCOMING
            val showCompleted = uiState.selectedFilter == ReminderFilter.ALL || uiState.selectedFilter == ReminderFilter.COMPLETED

            val hasAnyVisible = (showToday && uiState.todayReminders.isNotEmpty()) ||
                (showUpcoming && uiState.upcomingReminders.isNotEmpty()) ||
                (showCompleted && uiState.completedReminders.isNotEmpty())

            if (!hasAnyVisible) {
                item(key = "empty") {
                    EmptyStatePanel(
                        title = if (uiState.searchQuery.isNotBlank()) "No matching reminders" else stringResource(R.string.empty_reminders_title),
                        subtitle = if (uiState.searchQuery.isNotBlank()) "Try adjusting your search or filter." else stringResource(R.string.empty_reminders_subtitle)
                    )
                }
            } else {
                if (showToday && uiState.todayReminders.isNotEmpty()) {
                    item(key = "hdr_today") {
                        SectionHeaderLabel(
                            title = stringResource(R.string.section_today),
                            count = uiState.todayReminders.size
                        )
                    }
                    items(uiState.todayReminders, key = { "today_${it.id}" }) { reminder ->
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

                if (showUpcoming && uiState.upcomingReminders.isNotEmpty()) {
                    item(key = "hdr_upcoming") {
                        SectionHeaderLabel(
                            title = stringResource(R.string.section_upcoming),
                            count = uiState.upcomingReminders.size
                        )
                    }
                    items(uiState.upcomingReminders, key = { "upcoming_${it.id}" }) { reminder ->
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

                if (showCompleted && uiState.completedReminders.isNotEmpty()) {
                    item(key = "hdr_completed") {
                        SectionHeaderLabel(
                            title = stringResource(R.string.section_completed),
                            count = uiState.completedReminders.size
                        )
                    }
                    items(uiState.completedReminders, key = { "completed_${it.id}" }) { reminder ->
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

        FloatingActionButton(
            onClick = onCreateReminder,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_create_reminder")
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.create_reminder)
            )
        }
    }
}

@Composable
private fun NextUpcomingPanel(
    reminder: Reminder,
    category: Category?,
    use24Hour: Boolean,
    onClick: () -> Unit
) {
    val trigger = reminder.effectiveNextTriggerMillis() ?: reminder.scheduledTimeMillis
    val countdown = TimeFormatters.formatCountdown(trigger)
    val timeText = TimeFormatters.formatTime(trigger, use24Hour)
    val dateText = TimeFormatters.formatDateRelative(trigger)
    val catColor = Color(category?.colorHex ?: 0xFFA78BFA)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("next_upcoming_panel"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.section_next_up),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = countdown,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${category?.name ?: "Other"} • ${reminder.soundTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = catColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = timeText,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_reminders_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_search_input")
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 6.dp else 8.dp
            )
        )
    }
}

@Composable
private fun CategoryFilterPill(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit
) {
    val catColor = Color(category.colorHex)
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = if (selected) catColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) catColor else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = CategoryIconCatalog.iconForKey(category.iconKey),
                contentDescription = null,
                tint = if (selected) catColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) catColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
