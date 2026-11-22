package com.example.ui.navigation

import android.content.Context
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.PXReminderApplication
import com.example.ui.calendar.CalendarScreen
import com.example.ui.calendar.CalendarViewModel
import com.example.ui.create.CreateReminderScreen
import com.example.ui.create.CreateReminderViewModel
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.history.HistoryScreen
import com.example.ui.history.HistoryViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import java.time.ZoneId

object Routes {
    const val ROUTE_DASHBOARD = "dashboard"
    const val ROUTE_CALENDAR = "calendar"
    const val ROUTE_HISTORY = "history"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_CREATE = "create?reminderId={reminderId}&dateMillis={dateMillis}"

    fun createRoute(reminderId: Long = -1L, dateMillis: Long = -1L): String =
        "create?reminderId=$reminderId&dateMillis=$dateMillis"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.ROUTE_DASHBOARD, "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    TopLevelDestination(Routes.ROUTE_CALENDAR, "Calendar", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    TopLevelDestination(Routes.ROUTE_HISTORY, "History", Icons.Rounded.History, Icons.Outlined.History),
    TopLevelDestination(Routes.ROUTE_SETTINGS, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings)
)

class PXViewModelFactory(private val app: PXReminderApplication) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> DashboardViewModel(
                appContext = app.applicationContext,
                reminderRepository = app.reminderRepository,
                preferencesRepository = app.preferencesRepository,
                alarmScheduler = app.alarmScheduler
            ) as T

            modelClass.isAssignableFrom(CreateReminderViewModel::class.java) -> CreateReminderViewModel(
                appContext = app.applicationContext,
                reminderRepository = app.reminderRepository,
                preferencesRepository = app.preferencesRepository,
                alarmScheduler = app.alarmScheduler
            ) as T

            modelClass.isAssignableFrom(CalendarViewModel::class.java) -> CalendarViewModel(
                appContext = app.applicationContext,
                reminderRepository = app.reminderRepository,
                preferencesRepository = app.preferencesRepository,
                alarmScheduler = app.alarmScheduler
            ) as T

            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> HistoryViewModel(
                appContext = app.applicationContext,
                reminderRepository = app.reminderRepository,
                preferencesRepository = app.preferencesRepository,
                alarmScheduler = app.alarmScheduler
            ) as T

            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
                appContext = app.applicationContext,
                reminderRepository = app.reminderRepository,
                preferencesRepository = app.preferencesRepository,
                alarmScheduler = app.alarmScheduler
            ) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

@Composable
fun PXReminderAppShell(
    app: PXReminderApplication,
    openCreateOnLaunch: Boolean,
    onConsumedOpenCreate: () -> Unit
) {
    val navController = rememberNavController()
    val factory = PXViewModelFactory(app)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomOrRailNav = currentRoute in topLevelDestinations.map { it.route }

    LaunchedEffect(openCreateOnLaunch) {
        if (openCreateOnLaunch) {
            onConsumedOpenCreate()
            navController.navigate(Routes.createRoute())
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showBottomOrRailNav && !useNavigationRail) {
                    androidx.compose.foundation.layout.Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            topLevelDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(dest.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showBottomOrRailNav && useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.ROUTE_DASHBOARD,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Routes.ROUTE_DASHBOARD) {
                        val vm: DashboardViewModel = viewModel(factory = factory)
                        val state by vm.uiState.collectAsStateWithLifecycle()
                        DashboardScreen(
                            uiState = state,
                            onRefreshPermissions = vm::refreshPermissions,
                            onSelectFilter = vm::setFilter,
                            onSelectCategory = vm::setCategoryFilter,
                            onSearchQueryChange = vm::setSearchQuery,
                            onCreateReminder = { navController.navigate(Routes.createRoute()) },
                            onEditReminder = { id -> navController.navigate(Routes.createRoute(reminderId = id)) },
                            onToggleEnabled = vm::toggleEnabled,
                            onToggleCompleted = vm::toggleCompleted
                        )
                    }

                    composable(Routes.ROUTE_CALENDAR) {
                        val vm: CalendarViewModel = viewModel(factory = factory)
                        val state by vm.uiState.collectAsStateWithLifecycle()
                        CalendarScreen(
                            uiState = state,
                            onPreviousMonth = vm::previousMonth,
                            onNextMonth = vm::nextMonth,
                            onJumpToToday = vm::jumpToToday,
                            onSelectDate = vm::selectDate,
                            onCreateForDate = { date ->
                                val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                navController.navigate(Routes.createRoute(dateMillis = millis))
                            },
                            onEditReminder = { id -> navController.navigate(Routes.createRoute(reminderId = id)) },
                            onToggleEnabled = vm::toggleEnabled,
                            onToggleCompleted = vm::toggleCompleted
                        )
                    }

                    composable(Routes.ROUTE_HISTORY) {
                        val vm: HistoryViewModel = viewModel(factory = factory)
                        val state by vm.uiState.collectAsStateWithLifecycle()
                        HistoryScreen(
                            uiState = state,
                            onRestoreReminder = vm::restoreCompletedReminder,
                            onDeleteCompletedReminder = vm::deleteCompletedReminder,
                            onClearAllCompleted = vm::clearAllCompleted,
                            onClearEventLog = vm::clearEventLog
                        )
                    }

                    composable(Routes.ROUTE_SETTINGS) {
                        val vm: SettingsViewModel = viewModel(factory = factory)
                        val state by vm.uiState.collectAsStateWithLifecycle()
                        SettingsScreen(
                            uiState = state,
                            onRefreshPermissions = vm::refreshPermissions,
                            onClearStatusMessage = vm::clearStatusMessage,
                            onThemeModeChange = vm::setThemeMode,
                            onDefaultSoundChange = vm::setDefaultSound,
                            onDefaultVolumeChange = vm::setDefaultVolume,
                            onDefaultSnoozeChange = vm::setDefaultSnoozeMinutes,
                            onDefaultVibrationChange = vm::setDefaultVibration,
                            onGradualVolumeChange = vm::setGradualVolume,
                            onSnoozePlaySoundChange = vm::setSnoozePlaySound,
                            onUse24HourChange = vm::setUse24Hour,
                            onFirstDayOfWeekChange = vm::setFirstDayOfWeek,
                            onSaveCategory = vm::saveCategory,
                            onDeleteCategory = vm::deleteCategory,
                            onDeleteAllCompleted = vm::deleteAllCompleted,
                            onExportBackup = vm::exportBackupToUri,
                            onImportBackup = vm::importBackupFromUri,
                            onTestAlarm = vm::scheduleTestAlarm5s
                        )
                    }

                    composable(
                        route = Routes.ROUTE_CREATE,
                        arguments = listOf(
                            navArgument("reminderId") {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                            navArgument("dateMillis") {
                                type = NavType.LongType
                                defaultValue = -1L
                            }
                        )
                    ) { backStackEntry ->
                        val reminderId = backStackEntry.arguments?.getLong("reminderId") ?: -1L
                        val dateMillis = backStackEntry.arguments?.getLong("dateMillis") ?: -1L
                        val vm: CreateReminderViewModel = viewModel(factory = factory)
                        LaunchedEffect(reminderId, dateMillis) {
                            vm.initialize(
                                reminderId = reminderId.takeIf { it > 0L },
                                initialDateMillis = dateMillis.takeIf { it > 0L }
                            )
                        }
                        val state by vm.uiState.collectAsStateWithLifecycle()
                        CreateReminderScreen(
                            uiState = state,
                            onTitleChange = vm::updateTitle,
                            onDescriptionChange = vm::updateDescription,
                            onPrimaryDateChange = vm::updatePrimaryDate,
                            onPrimaryTimeChange = vm::updatePrimaryTime,
                            onAddScheduleSlot = vm::addScheduleSlot,
                            onRemoveScheduleSlot = vm::removeScheduleSlot,
                            onRepeatTypeChange = vm::updateRepeatType,
                            onToggleCustomDay = vm::toggleCustomDay,
                            onCategoryChange = vm::updateCategory,
                            onSaveCustomCategory = vm::saveCustomCategory,
                            onPriorityChange = vm::updatePriority,
                            onSoundChange = vm::updateSound,
                            onVibrationChange = vm::updateVibration,
                            onSnoozeDurationChange = vm::updateSnoozeDuration,
                            onSave = { vm.saveReminder { navController.popBackStack() } },
                            onDelete = { vm.deleteReminder { navController.popBackStack() } },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
