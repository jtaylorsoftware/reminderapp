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
        val reminder = Reminder()
        val alarm = Alarm(1, reminderId = reminder.id, triggerAt = currentLocalDateTime().plusHours(1))

        scheduleAlarmForReminder(context, alarm, reminder)

        intent = getCreatedIntentForAlarmStart(context, reminder.id)

        assertThat(intent).isNotNull()
    }

    @Test
    fun scheduleNextAlarm_populatedDb_setsAlarmForNextSoonest() {
        val now = currentLocalDateTime()

        val reminders = (1..2).map { Reminder() }

        // Save alarms with descending id but ascending time
        val alarms = reminders.mapIndexed { i, reminder ->
            Alarm(
                id = i.toLong(),
                reminderId = reminder.id,
                triggerAt = now.plusMinutes(10L * (i + 1))
            )
        }
        alarms.forEach { runBlocking { alarmRepo.save(it) } }

        runBlocking {
            reminderRepository.saveAll(reminders)
            scheduleNextAlarm(
                context,
                alarmRepo,
                reminderRepository,
            )

            intent = getCreatedIntentForAlarmStart(context, reminders[0].id)

            assertThat(intent).isNotNull()
            assertThat(alarmRepo.getFirstScheduledAlarm()!!.copy(id=0)).isEqualTo(alarms[0].copy(scheduled = true))
        }
    }

    // Sometimes (always?) runs twice, once fails with error:
    // java.lang.IllegalStateException: The component was not created. Check that you have added the HiltAndroidRule.
    // TODO: See if there's a way to fix, but the 2nd run succeeds so not vital
    @Test
    fun scheduleNextAlarm_clearsPreviouslyScheduled() {
        val now = currentLocalDateTime()

        val reminders = (1..4).map { Reminder() }

        // Save alarms with descending id but ascending time
        val alarms = reminders.mapIndexed { i, reminder ->
            Alarm(
                id = i.toLong(), reminderId = reminder.id, triggerAt = now.plusMinutes(i.toLong()),
                scheduled = if (i == 4) true else null
            )
        }
        alarms.forEach { runBlocking { alarmRepo.save(it) } }

        runBlocking {
            reminderRepository.saveAll(reminders)
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
        val reminder = Reminder()
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
        val reminder = Reminder()

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
        val reminder = Reminder()
        val alarm = Alarm(id = 1, reminderId = reminder.id)

        triggerNotification(context, reminder, alarm)

        intent = getCreatedIntentForAlarmActivity(context, reminder.id)

        assertThat(intent).isNotNull()
    }
}