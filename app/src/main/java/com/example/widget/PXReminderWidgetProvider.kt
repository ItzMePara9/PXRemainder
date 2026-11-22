package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.database.PXReminderDatabase
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.ReminderRepository
import com.example.domain.usecases.TimeFormatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PXReminderWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdater.updateWidgets(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object WidgetUpdater {

    const val EXTRA_OPEN_CREATE = "extra_open_create"

    suspend fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, PXReminderWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isNotEmpty()) {
            updateWidgets(context, manager, ids)
        }
    }

    suspend fun updateWidgets(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val db = PXReminderDatabase.getInstance(context.applicationContext)
        val repo = ReminderRepository(db)
        val prefs = PreferencesRepository(context.applicationContext).getSnapshot()
        val now = System.currentTimeMillis()

        val active = repo.getAllActiveReminders()
        val categories = repo.getAllCategoriesSnapshot().associateBy { it.id }

        val nextEntry = active
            .mapNotNull { reminder ->
                val trigger = reminder.effectiveNextTriggerMillis() ?: return@mapNotNull null
                if (trigger >= now - 60_000L) reminder to trigger else null
            }
            .minByOrNull { it.second }

        val openAppIntent = PendingIntent.getActivity(
            context,
            7001,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val quickCreateIntent = PendingIntent.getActivity(
            context,
            7002,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_CREATE, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_px_reminder)
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent)
            views.setOnClickPendingIntent(R.id.widget_quick_add, quickCreateIntent)

            if (nextEntry != null) {
                val (reminder, triggerMillis) = nextEntry
                val catName = categories[reminder.categoryId]?.name ?: "Reminder"
                val timeStr = TimeFormatters.formatTime(triggerMillis, prefs.use24HourFormat)
                val dateStr = TimeFormatters.formatDateRelative(triggerMillis)
                val countdown = TimeFormatters.formatCountdown(triggerMillis, now)

                views.setTextViewText(R.id.widget_reminder_title, reminder.title)
                views.setTextViewText(R.id.widget_reminder_time, "$dateStr • $timeStr")
                views.setTextViewText(R.id.widget_reminder_meta, "$catName • $countdown")
                views.setViewVisibility(R.id.widget_reminder_meta, View.VISIBLE)
            } else {
                views.setTextViewText(
                    R.id.widget_reminder_title,
                    context.getString(R.string.widget_no_upcoming)
                )
                views.setTextViewText(
                    R.id.widget_reminder_time,
                    context.getString(R.string.widget_tap_to_create)
                )
                views.setViewVisibility(R.id.widget_reminder_meta, View.GONE)
            }

            manager.updateAppWidget(widgetId, views)
        }
    }
}
