package com.github.jeremyt135.reminderapp.broadcast

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@HiltAndroidTest
@SmallTest
class AlarmUtilTest {
    lateinit var context: Context

    private var intent: PendingIntent? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun teardown() {
        intent?.cancel()
        intent = null
        NotificationManagerCompat.from(context).cancelAll()
    }

    @Test
    fun scheduleAlarmForReminder_oneOffInPast_returnsNull() {
        val reminder = Reminder(
            daysOfWeek = sortedSetOf(),
            time = LocalTime.now().minusMinutes(2)
        )

        val today = LocalDate.now().dayOfWeek

        assertThat(scheduleAlarmForReminder(context, reminder, today)).isNull()
        intent = getCreatedIntentForAlarmStart(context, reminder.id)
    }

    @Test
    fun scheduleAlarmForReminder_goodDateTime_setsAlarm() {
        val reminder = Reminder(
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
        intent = getCreatedIntentForAlarmStart(context, alarm.reminderId)
        assertThat(intent).isNotNull()
    }

    @Test
    fun scheduleAlarm_setsAlarm() {
        val alarm = Alarm(1, 1, LocalDateTime.now().plusHours(1).toEpochMilli())

        scheduleAlarm(context, alarm)

        intent = getCreatedIntentForAlarmStart(context, alarm.reminderId)

        assertThat(intent).isNotNull()
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
    fun triggerNotification_createsAlarmActivityIntent(){
        val reminder = Reminder(id=1)
        val alarm = Alarm(id=1,reminderId = reminder.id, triggerAt = 0)

        triggerNotification(context, reminder, alarm.id)

        intent = getCreatedIntentForAlarmActivity(context, reminder.id)

        assertThat(intent).isNotNull()
    }
}