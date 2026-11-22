package com.example.alarm

import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.example.audio.AlarmAudioManager
import com.example.data.database.PXReminderDatabase
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.ReminderRepository
import com.example.domain.models.ActiveAlarm
import com.example.domain.models.AlarmEventType
import com.example.domain.models.RepeatType
import com.example.domain.usecases.RecurrenceCalculator
import com.example.utils.PermissionHelper
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var audioManager: AlarmAudioManager
    private lateinit var repository: ReminderRepository
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var scheduler: AlarmScheduler
    private var serviceWakeLock: PowerManager.WakeLock? = null
    private var isForegroundStarted = false
    private var isRingingAudio = false

    override fun onCreate() {
        super.onCreate()
        audioManager = AlarmAudioManager(this)
        val db = PXReminderDatabase.getInstance(this)
        repository = ReminderRepository(db)
        preferencesRepository = PreferencesRepository(this)
        scheduler = AlarmScheduler(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: AlarmScheduler.ACTION_ALARM_TRIGGER
        val reminderId = intent?.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L) ?: -1L
        val slotIndex = intent?.getIntExtra(AlarmScheduler.EXTRA_SLOT_INDEX, 0) ?: 0
        val isSnooze = intent?.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false) ?: false

        // Populate AlarmStateHolder SYNCHRONOUSLY before startForeground so the initial
        // foreground notification ALREADY carries fullScreenIntent (Android does not fire
        // fullScreenIntent on subsequent nm.notify() updates to an existing notification).
        var synchronousAlarm: ActiveAlarm? = null
        if (action == AlarmScheduler.ACTION_ALARM_TRIGGER && reminderId > 0L) {
            val title = intent?.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "PXReminder Alarm"
            val description = intent?.getStringExtra(AlarmScheduler.EXTRA_DESCRIPTION) ?: ""
            val scheduledMillis = intent?.getLongExtra(
                AlarmScheduler.EXTRA_SCHEDULED_MILLIS,
                System.currentTimeMillis()
            ) ?: System.currentTimeMillis()
            val categoryName = intent?.getStringExtra(AlarmScheduler.EXTRA_CATEGORY_NAME) ?: "Reminder"
            val categoryColor = intent?.getLongExtra(AlarmScheduler.EXTRA_CATEGORY_COLOR, 0xFFA78BFA)
                ?: 0xFFA78BFA
            val soundUri = intent?.getStringExtra(AlarmScheduler.EXTRA_SOUND_URI) ?: "builtin:classic_alarm"
            val vibration = intent?.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATION_ENABLED, true) ?: true
            val snoozeMinutes = intent?.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_MINUTES, 10) ?: 10

            synchronousAlarm = ActiveAlarm(
                reminderId = reminderId,
                slotIndex = slotIndex,
                title = title,
                description = description,
                scheduledTimeMillis = scheduledMillis,
                categoryName = categoryName,
                categoryColorHex = categoryColor,
                soundUri = soundUri,
                vibrationEnabled = vibration,
                snoozeDurationMinutes = snoozeMinutes,
                isSnooze = isSnooze
            )
            AlarmStateHolder.addOrUpdate(synchronousAlarm)
        }

        ensureForegroundStarted()

        when (action) {
            AlarmScheduler.ACTION_ALARM_TRIGGER -> {
                if (reminderId > 0L) {
                    handleTrigger(reminderId, slotIndex, isSnooze, synchronousAlarm)
                } else {
                    stopIfIdle()
                }
            }

            AlarmScheduler.ACTION_ALARM_SNOOZE -> {
                val snoozeMinutes = intent?.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_MINUTES, 10) ?: 10
                handleSnooze(reminderId, slotIndex, snoozeMinutes)
            }

            AlarmScheduler.ACTION_ALARM_DISMISS -> {
                handleDismiss(reminderId, slotIndex)
            }

            ACTION_DISMISS_ALL -> {
                handleDismissAll()
            }
        }

        return START_NOT_STICKY
    }

    private fun ensureForegroundStarted() {
        val currentAlarms = AlarmStateHolder.activeAlarms.value
        val notification = if (currentAlarms.isNotEmpty()) {
            AlarmNotificationManager.buildAlarmNotification(this, currentAlarms)
        } else {
            AlarmNotificationManager.buildPlaceholderNotification(this)
        }

        if (!isForegroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    AlarmNotificationManager.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(AlarmNotificationManager.NOTIFICATION_ID, notification)
            }
            isForegroundStarted = true
        } else if (currentAlarms.isNotEmpty()) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(AlarmNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    private fun acquireServiceWakeLock() {
        if (serviceWakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            serviceWakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PXReminder::AlarmServiceWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
        }
        if (serviceWakeLock?.isHeld == false) {
            serviceWakeLock?.acquire(30 * 60_000L)
        }
    }

    private fun releaseServiceWakeLock() {
        if (serviceWakeLock?.isHeld == true) {
            runCatching { serviceWakeLock?.release() }
        }
    }

    private fun handleTrigger(
        reminderId: Long,
        slotIndex: Int,
        isSnooze: Boolean,
        synchronousAlarm: ActiveAlarm?
    ) {
        // Attempt immediate full-screen UI launch (covers unlocked screen when permitted)
        if (PermissionHelper.canUseFullScreenIntent(this) && synchronousAlarm != null) {
            launchRingingActivity(synchronousAlarm)
        }

        serviceScope.launch {
            val isTestAlarm = reminderId == TEST_ALARM_ID
            val (reminder, category, prefs) = withContext(Dispatchers.IO) {
                val r = if (isTestAlarm) null else repository.getReminderById(reminderId)
                val cat = r?.let { repository.getCategoryById(it.categoryId) }
                val p = preferencesRepository.getSnapshot()
                Triple(r, cat, p)
            }

            if (!isTestAlarm && (reminder == null || !reminder.isEnabled || reminder.isCompleted)) {
                AlarmStateHolder.remove(reminderId, slotIndex)
                refreshOrStopAfterAction()
                return@launch
            }

            val resolvedAlarm = if (reminder != null) {
                val scheduledTime = when {
                    isSnooze && reminder.snoozedUntilMillis != null -> reminder.snoozedUntilMillis
                    slotIndex == 0 -> reminder.scheduledTimeMillis
                    else -> reminder.additionalTimesMillis.getOrNull(slotIndex - 1) ?: reminder.scheduledTimeMillis
                }
                ActiveAlarm(
                    reminderId = reminder.id,
                    slotIndex = slotIndex,
                    title = reminder.title,
                    description = reminder.description,
                    scheduledTimeMillis = scheduledTime,
                    categoryName = category?.name ?: "Reminder",
                    categoryColorHex = category?.colorHex ?: 0xFFA78BFA,
                    soundUri = reminder.soundUri,
                    vibrationEnabled = reminder.vibrationEnabled,
                    snoozeDurationMinutes = reminder.snoozeDurationMinutes,
                    isSnooze = isSnooze
                )
            } else {
                synchronousAlarm?.copy(soundUri = prefs.defaultSoundUri)
            }

            if (resolvedAlarm == null) {
                stopIfIdle()
                return@launch
            }

            AlarmStateHolder.addOrUpdate(resolvedAlarm)
            updateNotification()

            if (!isRingingAudio) {
                acquireServiceWakeLock()
                val shouldPlaySound = if (isSnooze) prefs.snoozePlaySound else true
                audioManager.startAlarm(
                    soundUri = resolvedAlarm.soundUri,
                    volume = prefs.defaultAlarmVolume,
                    gradualIncrease = prefs.gradualVolumeIncrease,
                    vibrationEnabled = resolvedAlarm.vibrationEnabled,
                    playSound = shouldPlaySound,
                    scope = serviceScope
                )
                isRingingAudio = true
            }
        }
    }

    private fun handleSnooze(reminderId: Long, slotIndex: Int, snoozeMinutes: Int) {
        serviceScope.launch {
            val currentAlarm = AlarmStateHolder.activeAlarms.value
                .firstOrNull { it.reminderId == reminderId && it.slotIndex == slotIndex }
            AlarmStateHolder.remove(reminderId, slotIndex)
            if (reminderId != TEST_ALARM_ID) {
                withContext(Dispatchers.IO) {
                    val reminder = repository.getReminderById(reminderId)
                    if (reminder != null) {
                        val cat = repository.getCategoryById(reminder.categoryId)
                        val snoozeUntil = scheduler.scheduleSnooze(
                            reminderId = reminderId,
                            slotIndex = slotIndex,
                            snoozeMinutes = snoozeMinutes,
                            reminder = reminder,
                            category = cat,
                            fallbackAlarm = currentAlarm
                        )
                        val updated = if (slotIndex == 0) {
                            reminder.copy(snoozedUntilMillis = snoozeUntil)
                        } else {
                            val updatedExtras = reminder.additionalTimesMillis.toMutableList()
                            val idx = slotIndex - 1
                            if (idx in updatedExtras.indices) {
                                updatedExtras[idx] = snoozeUntil
                            }
                            reminder.copy(additionalTimesMillis = updatedExtras)
                        }
                        repository.saveReminder(updated)
                        repository.recordHistory(
                            reminderId = reminder.id,
                            title = reminder.title,
                            categoryName = cat?.name ?: "Reminder",
                            categoryColorHex = cat?.colorHex ?: 0xFFA78BFA,
                            eventType = AlarmEventType.SNOOZED
                        )
                    }
                    WidgetUpdater.updateAll(this@AlarmService)
                }
            }
            refreshOrStopAfterAction()
        }
    }

    private fun handleDismiss(reminderId: Long, slotIndex: Int) {
        serviceScope.launch {
            AlarmStateHolder.remove(reminderId, slotIndex)
            if (reminderId != TEST_ALARM_ID) {
                withContext(Dispatchers.IO) {
                    val reminder = repository.getReminderById(reminderId)
                    if (reminder != null) {
                        val now = System.currentTimeMillis()
                        val cat = repository.getCategoryById(reminder.categoryId)
                        val updated: com.example.domain.models.Reminder
                        var completedNow = false

                        if (reminder.repeatType != RepeatType.NEVER) {
                            if (slotIndex == 0) {
                                val nextTime = RecurrenceCalculator.nextOccurrenceAfter(
                                    baseMillis = reminder.scheduledTimeMillis,
                                    repeatType = reminder.repeatType,
                                    customDaysMask = reminder.customDaysMask,
                                    afterMillis = now
                                ) ?: (now + 24 * 60 * 60_000L)
                                updated = reminder.copy(
                                    scheduledTimeMillis = nextTime,
                                    snoozedUntilMillis = null
                                )
                            } else {
                                val extras = reminder.additionalTimesMillis.toMutableList()
                                val idx = slotIndex - 1
                                if (idx in extras.indices) {
                                    extras[idx] = RecurrenceCalculator.nextOccurrenceAfter(
                                        baseMillis = extras[idx],
                                        repeatType = reminder.repeatType,
                                        customDaysMask = reminder.customDaysMask,
                                        afterMillis = now
                                    ) ?: extras[idx]
                                }
                                updated = reminder.copy(
                                    additionalTimesMillis = extras,
                                    snoozedUntilMillis = null
                                )
                            }
                            repository.saveReminder(updated)
                            scheduler.scheduleReminder(updated, cat)
                        } else {
                            val remainingFutureExtras = reminder.additionalTimesMillis.filterIndexed { idx, time ->
                                (idx + 1 != slotIndex) && time > now
                            }
                            if (slotIndex == 0) {
                                if (remainingFutureExtras.isNotEmpty()) {
                                    val nextPrimary = remainingFutureExtras.min()
                                    val rest = remainingFutureExtras.filterNot { it == nextPrimary }
                                    updated = reminder.copy(
                                        scheduledTimeMillis = nextPrimary,
                                        additionalTimesMillis = rest,
                                        snoozedUntilMillis = null
                                    )
                                    repository.saveReminder(updated)
                                    scheduler.scheduleReminder(updated, cat)
                                } else {
                                    completedNow = true
                                    updated = reminder.copy(
                                        additionalTimesMillis = emptyList(),
                                        snoozedUntilMillis = null,
                                        isEnabled = false,
                                        isCompleted = true,
                                        completedAtMillis = now
                                    )
                                    repository.saveReminder(updated)
                                    scheduler.cancelReminder(updated.id)
                                }
                            } else {
                                if (reminder.scheduledTimeMillis > now || remainingFutureExtras.isNotEmpty()) {
                                    updated = reminder.copy(
                                        additionalTimesMillis = remainingFutureExtras,
                                        snoozedUntilMillis = null
                                    )
                                    repository.saveReminder(updated)
                                    scheduler.scheduleReminder(updated, cat)
                                } else {
                                    completedNow = true
                                    updated = reminder.copy(
                                        additionalTimesMillis = emptyList(),
                                        snoozedUntilMillis = null,
                                        isEnabled = false,
                                        isCompleted = true,
                                        completedAtMillis = now
                                    )
                                    repository.saveReminder(updated)
                                    scheduler.cancelReminder(updated.id)
                                }
                            }
                        }

                        repository.recordHistory(
                            reminderId = reminder.id,
                            title = reminder.title,
                            categoryName = cat?.name ?: "Reminder",
                            categoryColorHex = cat?.colorHex ?: 0xFFA78BFA,
                            eventType = if (completedNow) AlarmEventType.COMPLETED else AlarmEventType.DISMISSED
                        )
                    }
                    WidgetUpdater.updateAll(this@AlarmService)
                }
            }
            refreshOrStopAfterAction()
        }
    }

    private fun handleDismissAll() {
        val current = AlarmStateHolder.activeAlarms.value.toList()
        if (current.isEmpty()) {
            stopServiceCompletely()
            return
        }
        current.forEach { alarm ->
            handleDismiss(alarm.reminderId, alarm.slotIndex)
        }
    }

    private fun refreshOrStopAfterAction() {
        val remaining = AlarmStateHolder.activeAlarms.value
        if (remaining.isEmpty()) {
            stopServiceCompletely()
        } else {
            updateNotification()
        }
    }

    private fun stopIfIdle() {
        if (AlarmStateHolder.activeAlarms.value.isEmpty()) {
            stopServiceCompletely()
        }
    }

    private fun updateNotification() {
        val alarms = AlarmStateHolder.activeAlarms.value
        if (alarms.isNotEmpty()) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(
                AlarmNotificationManager.NOTIFICATION_ID,
                AlarmNotificationManager.buildAlarmNotification(this, alarms)
            )
        }
    }

    private fun launchRingingActivity(alarm: ActiveAlarm) {
        runCatching {
            val pendingIntent = AlarmNotificationManager.createFullScreenPendingIntent(this, alarm)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                pendingIntent.send(this, 0, null, null, null, null, options.toBundle())
            } else {
                val intent = Intent(this, AlarmRingingActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NO_USER_ACTION
                    )
                    putExtra(AlarmScheduler.EXTRA_REMINDER_ID, alarm.reminderId)
                    putExtra(AlarmScheduler.EXTRA_SLOT_INDEX, alarm.slotIndex)
                    putExtra(AlarmScheduler.EXTRA_TITLE, alarm.title)
                    putExtra(AlarmScheduler.EXTRA_DESCRIPTION, alarm.description)
                    putExtra(AlarmScheduler.EXTRA_SCHEDULED_MILLIS, alarm.scheduledTimeMillis)
                    putExtra(AlarmScheduler.EXTRA_CATEGORY_NAME, alarm.categoryName)
                    putExtra(AlarmScheduler.EXTRA_CATEGORY_COLOR, alarm.categoryColorHex)
                    putExtra(AlarmScheduler.EXTRA_SOUND_URI, alarm.soundUri)
                    putExtra(AlarmScheduler.EXTRA_VIBRATION_ENABLED, alarm.vibrationEnabled)
                    putExtra(AlarmScheduler.EXTRA_SNOOZE_MINUTES, alarm.snoozeDurationMinutes)
                }
                startActivity(intent)
            }
        }.onFailure {
            Log.w("AlarmService", "Background activity launch blocked; relying on full-screen notification", it)
        }
    }

    private fun stopServiceCompletely() {
        isRingingAudio = false
        audioManager.stopAll()
        releaseServiceWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForegroundStarted = false
        stopSelf()
    }

    override fun onDestroy() {
        isRingingAudio = false
        audioManager.stopAll()
        releaseServiceWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_DISMISS_ALL = "com.example.action.ALARM_DISMISS_ALL"
        const val TEST_ALARM_ID = 999999L
    }
}
