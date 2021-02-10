package com.github.jeremyt135.reminderapp.broadcast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.data.local.Alarm
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@HiltAndroidTest
class AlarmUtilTest {
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

        val context = ApplicationProvider.getApplicationContext<Context>()
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

        val context = ApplicationProvider.getApplicationContext<Context>()
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
}