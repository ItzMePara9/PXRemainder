package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.database.PXReminderDatabase
import com.example.data.repository.ReminderRepository
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores all active reminders after device reboot, timezone/clock/date changes,
 * app updates, or exact alarm permission changes.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val supported = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
        if (action !in supported) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = PXReminderDatabase.getInstance(context.applicationContext)
                val repo = ReminderRepository(db)
                val scheduler = AlarmScheduler(context.applicationContext)

                val active = repo.getAllActiveReminders()
                val categoriesById = repo.getAllCategoriesSnapshot().associateBy { it.id }
                val updated = scheduler.rescheduleAll(active, categoriesById)
                updated.forEach { reminder ->
                    repo.saveReminder(reminder)
                }
                WidgetUpdater.updateAll(context.applicationContext)
                Log.i("BootReceiver", "Rescheduled ${updated.size} active reminders on $action")
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error restoring alarms on $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
