package com.example

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.alarm.AlarmScheduler
import com.example.data.database.PXReminderDatabase
import com.example.data.repository.ReminderRepository
import com.example.domain.models.Priority
import com.example.domain.models.Reminder
import com.example.domain.models.RepeatType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlarmEngineRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: PXReminderDatabase
    private lateinit var repository: ReminderRepository
    private lateinit var scheduler: AlarmScheduler

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PXReminderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ReminderRepository(db)
        scheduler = AlarmScheduler(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `save reminder schedules alarm and cancel removes it`() = runBlocking {
        val futureTime = System.currentTimeMillis() + 120_000L
        val reminder = Reminder(
            title = "Test Alarm",
            description = "Two minutes ahead",
            scheduledTimeMillis = futureTime,
            additionalTimesMillis = listOf(futureTime + 60_000L),
            repeatType = RepeatType.NEVER,
            priority = Priority.HIGH
        )
        val savedId = repository.saveReminder(reminder)
        val loaded = repository.getReminderById(savedId)!!
        assertEquals("Test Alarm", loaded.title)

        val scheduled = scheduler.scheduleReminder(loaded)
        assertTrue(scheduled)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        assertTrue(shadowAlarmManager.scheduledAlarms.isNotEmpty())

        scheduler.cancelReminder(savedId)
        // After cancellation, scheduledAlarms should be cleared
        assertNull(shadowAlarmManager.nextScheduledAlarm)
    }
}
