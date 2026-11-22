package com.example.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.domain.models.ActiveAlarm
import com.example.utils.PermissionHelper

object AlarmNotificationManager {

    const val CHANNEL_ID = "pxreminder_alarm_channel_v1"
    const val NOTIFICATION_ID = 41001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.alarm_notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.alarm_notification_channel_desc)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                    setSound(null, null)
                    enableVibration(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun createFullScreenPendingIntent(
        context: Context,
        primary: ActiveAlarm?
    ): PendingIntent {
        val fullScreenIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
            if (primary != null) {
                putExtra(AlarmScheduler.EXTRA_REMINDER_ID, primary.reminderId)
                putExtra(AlarmScheduler.EXTRA_SLOT_INDEX, primary.slotIndex)
                putExtra(AlarmScheduler.EXTRA_TITLE, primary.title)
                putExtra(AlarmScheduler.EXTRA_DESCRIPTION, primary.description)
                putExtra(AlarmScheduler.EXTRA_SCHEDULED_MILLIS, primary.scheduledTimeMillis)
                putExtra(AlarmScheduler.EXTRA_CATEGORY_NAME, primary.categoryName)
                putExtra(AlarmScheduler.EXTRA_CATEGORY_COLOR, primary.categoryColorHex)
                putExtra(AlarmScheduler.EXTRA_SOUND_URI, primary.soundUri)
                putExtra(AlarmScheduler.EXTRA_VIBRATION_ENABLED, primary.vibrationEnabled)
                putExtra(AlarmScheduler.EXTRA_SNOOZE_MINUTES, primary.snoozeDurationMinutes)
                putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, primary.isSnooze)
            }
        }
        return PendingIntent.getActivity(
            context,
            9001,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildPlaceholderNotification(context: Context): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alarm)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Starting alarm…")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()
    }

    fun buildAlarmNotification(
        context: Context,
        activeAlarms: List<ActiveAlarm>
    ): Notification {
        ensureChannel(context)
        val primary = activeAlarms.firstOrNull()
        val title = primary?.title ?: context.getString(R.string.app_name)
        val extraCount = (activeAlarms.size - 1).coerceAtLeast(0)
        val contentText = buildString {
            if (!primary?.description.isNullOrBlank()) {
                append(primary?.description)
            } else {
                append(primary?.categoryName ?: "Alarm ringing")
            }
            if (extraCount > 0) {
                append(" • +$extraCount more alarm${if (extraCount > 1) "s" else ""}")
            }
        }

        val fullScreenPendingIntent = createFullScreenPendingIntent(context, primary)

        val targetId = primary?.reminderId ?: -1L
        val targetSlot = primary?.slotIndex ?: 0
        val snoozeMins = primary?.snoozeDurationMinutes ?: 10

        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_ALARM_SNOOZE
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, targetId)
            putExtra(AlarmScheduler.EXTRA_SLOT_INDEX, targetSlot)
            putExtra(AlarmScheduler.EXTRA_SNOOZE_MINUTES, snoozeMins)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            9002,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_ALARM_DISMISS
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, targetId)
            putExtra(AlarmScheduler.EXTRA_SLOT_INDEX, targetSlot)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            9003,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alarm)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText(primary?.categoryName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(0, "Snooze (${snoozeMins}m)", snoozePendingIntent)
            .addAction(0, "Dismiss", dismissPendingIntent)

        if (PermissionHelper.canUseFullScreenIntent(context)) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        return builder.build()
    }
}
