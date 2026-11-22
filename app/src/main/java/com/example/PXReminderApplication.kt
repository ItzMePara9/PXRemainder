package com.example

import android.app.Application
import com.example.alarm.AlarmNotificationManager
import com.example.alarm.AlarmScheduler
import com.example.data.database.PXReminderDatabase
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PXReminderApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: PXReminderDatabase by lazy { PXReminderDatabase.getInstance(this) }
    val reminderRepository: ReminderRepository by lazy { ReminderRepository(database) }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(this) }
    val alarmScheduler: AlarmScheduler by lazy { AlarmScheduler(this) }

    override fun onCreate() {
        super.onCreate()
        AlarmNotificationManager.ensureChannel(this)
        applicationScope.launch {
            reminderRepository.getAllCategoriesSnapshot()
        }
    }
}
