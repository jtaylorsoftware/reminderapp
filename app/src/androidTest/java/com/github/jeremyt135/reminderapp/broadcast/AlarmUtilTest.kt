package com.github.jeremyt135.reminderapp.broadcast

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.FakeAlarmRepository
import com.github.jeremyt135.reminderapp.data.FakeReminderRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.justRun
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

@HiltAndroidTest
@SmallTest
class AlarmUtilTest {
    lateinit var context: Context

    private var intent: PendingIntent? = null

    private var alarmCache = mutableListOf<Alarm>()
    private var alarmRepo: AlarmRepository = FakeAlarmRepository(alarmCache)
    private var reminderCache = mutableListOf<Reminder>()
    private var reminderRepository: ReminderRepository = FakeReminderRepository(
        reminderCache, alarmCache, mutableListOf()
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun teardown() {
        intent?.cancel()
        intent = null
        alarmCache.clear()
        reminderCache.clear()
        NotificationManagerCompat.from(context).cancelAll()
    }

    @Test
    fun scheduleAlarmForReminder_setsAlarm() {
        val alarm = Alarm(1, 1, currentLocalDateTime().plusHours(1))
        val reminder = Reminder(id = 1)

        scheduleAlarmForReminder(context, alarm, reminder)

        intent = getCreatedIntentForAlarmStart(context, alarm, reminder)

        assertThat(intent).isNotNull()
    }

    @Test
    fun scheduleNextAlarm_populatedDb_setsAlarmForNextSoonest() {
        val now = currentLocalDateTime()

        // Save alarms with descending id but ascending time
        val alarms = listOf(
            Alarm(id = 1, reminderId = 1, triggerAt = now.plusMinutes(10)),
            Alarm(id = 2, reminderId = 2, triggerAt = now.plusMinutes(20)),
        )
        alarms.forEach { runBlocking { alarmRepo.save(it) } }

        val reminder = Reminder(id = 1)
        runBlocking {
            reminderRepository.save(reminder)
            scheduleNextAlarm(
                context,
                alarmRepo,
                reminderRepository,
            )

            intent = getCreatedIntentForAlarmStart(context, alarms[0], reminder)

            assertThat(intent).isNotNull()
            assertThat(alarmRepo.getFirstScheduledAlarm()).isEqualTo(alarms[0].copy(scheduled = true))
        }
    }

    @Test
    fun scheduleNextAlarm_clearsPreviouslyScheduled() {
        val now = currentLocalDateTime()

        // Save alarms with descending id but ascending time
        val alarms = listOf(
            Alarm(id = 2, reminderId = 1, triggerAt = now.plusMinutes(2)),
            Alarm(id = 1, reminderId = 2, triggerAt = now.plusMinutes(3)),
            Alarm(
                id = 4,
                reminderId = 3,
                triggerAt = now.plusMinutes(5),
                scheduled = true
            ),
            Alarm(id = 3, reminderId = 4, triggerAt = now.plusMinutes(4))
        )
        alarms.forEach { runBlocking { alarmRepo.save(it) } }

        val reminder = Reminder(id = 1)
        runBlocking {
            reminderRepository.save(reminder)
            scheduleNextAlarm(
                context,
                alarmRepo,
                reminderRepository,
            )

            assertThat(alarmRepo.findById(4)!!.scheduled).isNull()
        }
    }

    @Test
    fun scheduleNextAlarm_noAlarms_doesNotSchedule() {
        val reminder = Reminder(id = 1)
        runBlocking {
            reminderRepository.save(reminder)
            scheduleNextAlarm(context, alarmRepo, reminderRepository)
        }

        mockkStatic(::scheduleAlarmForReminder)
        justRun { scheduleAlarmForReminder(context, any(), any()) }

        verify(exactly = 0) {
            scheduleAlarmForReminder(context, any(), any())
        }
    }

    @Test
    fun buildNotificationForAlarmIntent_createsActionIntents() {
        val reminder = Reminder(id = 1)

        // Build notification which should create Intents for skip, snooze, complete
        buildNotificationForAlarm(
            context,
            getIntentForAlarmActivity(context, reminder.id),
            reminder
        )

        assertThat(getCreatedIntentForAlarmSnooze(context, reminder.id)).isNotNull()
        assertThat(getCreatedIntentForAlarmSkip(context, reminder.id)).isNotNull()
        assertThat(getCreatedIntentForAlarmComplete(context, reminder.id)).isNotNull()
    }

    @Test
    fun triggerNotification_createsAlarmActivityIntent() {
        val reminder = Reminder(id = 1)
        val alarm = Alarm(id = 1, reminderId = reminder.id)

        triggerNotification(context, reminder, alarm.id)

        intent = getCreatedIntentForAlarmActivity(context, reminder.id)

        assertThat(intent).isNotNull()
    }
}