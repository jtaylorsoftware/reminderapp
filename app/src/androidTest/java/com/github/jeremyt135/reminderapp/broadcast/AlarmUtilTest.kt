package com.github.jeremyt135.reminderapp.broadcast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@HiltAndroidTest
@SmallTest
class AlarmUtilTest {
    lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
    }

    @Test
    fun scheduleAlarmForReminder_completedReminder_returnsNull() {
        val reminder = Reminder(status = ReminderStatus.COMPLETED)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val today = LocalDate.now().dayOfWeek

        assertThat(scheduleAlarmForReminder(context, reminder, today)).isNull()
    }

    @Test
    fun scheduleAlarmForReminder_oneOffInPast_returnsNull() {
        val reminder = Reminder(
            status = ReminderStatus.PENDING,
            daysOfWeek = sortedSetOf(),
            time = LocalTime.now().minusHours(1)
        )

        val today = LocalDate.now().dayOfWeek

        assertThat(scheduleAlarmForReminder(context, reminder, today)).isNull()
    }

    @Test
    fun scheduleAlarmForReminder_goodDateTime_setsAlarm() {
        val reminder = Reminder(
            status = ReminderStatus.PENDING,
            daysOfWeek = sortedSetOf(),
            time = LocalTime.now().plusHours(1)
        )

        val today = LocalDate.now().dayOfWeek

        // Set the alarm's triggerAt to match the reminder's time
        val alarmTime =
            reminder.getNextTimeForAlarm(today)!!
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val alarm = Alarm(reminderId = reminder.id, triggerAt = alarmTime)

        // Verify alarm was scheduled without failure
        assertThat(scheduleAlarmForReminder(context, reminder, today)).isNotNull()

        // Verify that AlarmManager did set the alarm with the correct intent
        val intent = getCreatedIntentForAlarmReceiver(context, alarm.reminderId)
        assertThat(intent).isNotNull()
    }

    @Test
    fun scheduleAlarm_setsAlarm() {
        val alarm = Alarm(1, 1, LocalDateTime.now().plusHours(1).toEpochMilli())

        scheduleAlarm(context, alarm)

        val intent = getCreatedIntentForAlarmReceiver(context, alarm.reminderId)

        assertThat(intent).isNotNull()
    }

    @Test
    fun alarmShouldTriggerNow_mismatchedInput_returnsFalse() {
        val alarm = Alarm(1, 5, 0)
        val reminder = Reminder(id = 1)

        val now = LocalDateTime.now()
        assertThat(alarmShouldTriggerNow(reminder, alarm, now)).isFalse()
    }

    @Test
    fun alarmShouldTriggerNow_inPast_returnsFalse() {
        val reminder = mockk<Reminder>()
        every { reminder.id } returns 1

        val now = LocalDateTime.now()
        // Set triggerAt to time in past
        val triggerAt = now.minusHours(1).truncatedTo(ChronoUnit.MINUTES).toEpochMilli()
        val alarm = Alarm(1, 1, triggerAt)

        every { reminder.alarmCanBeSetToday(now.dayOfWeek) } returns true

        assertThat(alarmShouldTriggerNow(reminder, alarm, now)).isFalse()
    }

    @Test
    fun alarmShouldTriggerNow_inFuture_returnsFalse() {
        val reminder = mockk<Reminder>()
        every { reminder.id } returns 1

        val now = LocalDateTime.now()
        // Set triggerAt to time in future
        val triggerAt = now.plusHours(1).truncatedTo(ChronoUnit.MINUTES).toEpochMilli()
        val alarm = Alarm(1, 1, triggerAt)

        every { reminder.alarmCanBeSetToday(now.dayOfWeek) } returns true

        assertThat(alarmShouldTriggerNow(reminder, alarm, now)).isFalse()
    }

    @Test
    fun alarmShouldTriggerNow_triggerAtIsNow_returnsTrue() {
        val reminder = mockk<Reminder>()
        every { reminder.id } returns 1

        val now = LocalDateTime.now()
        // Set triggerAt to now
        val triggerAt = now.truncatedTo(ChronoUnit.MINUTES).toEpochMilli()
        val alarm = Alarm(1, 1, triggerAt)

        every { reminder.alarmCanBeSetToday(now.dayOfWeek) } returns true

        assertThat(alarmShouldTriggerNow(reminder, alarm, now)).isTrue()
    }

    @Test
    fun buildNotificationForAlarmIntent_createsActionIntents() {
        val reminder = Reminder(id = 1)

        // Build notification which should create Intents for skip, snooze, complete
        buildNotificationForAlarmIntent(
            context,
            getIntentForAlarmActivity(context, reminder.id),
            reminder
        )

        assertThat(getCreatedIntentForSnoozeReceiver(context, reminder.id)).isNotNull()
        assertThat(getCreatedIntentForSkipReceiver(context, reminder.id)).isNotNull()
        assertThat(getCreatedIntentForCompleteReceiver(context, reminder.id)).isNotNull()
    }

    @Test
    fun triggerNotification_createsAlarmActivityIntent(){
        val reminder = Reminder(id=1)

        triggerNotification(context, reminder)

        assertThat(getCreatedIntentForAlarmActivity(context, reminder.id)).isNotNull()
    }
}