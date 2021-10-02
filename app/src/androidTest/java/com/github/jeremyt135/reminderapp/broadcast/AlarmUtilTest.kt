package com.github.jeremyt135.reminderapp.broadcast

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import com.github.jeremyt135.reminderapp.data.*
import com.github.jeremyt135.reminderapp.data.domain.*
import com.github.jeremyt135.reminderapp.data.local.FakeHistoryLocalDataSource
import com.github.jeremyt135.reminderapp.util.currentEpochMilli
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.github.jeremyt135.reminderapp.util.currentLocalTime
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.justRun
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

@ExperimentalCoroutinesApi
@HiltAndroidTest
@SmallTest
class AlarmUtilTest {
    @get:Rule
    var hiltAndroidRule = HiltAndroidRule(this)

    lateinit var context: Context

    private var intent: PendingIntent? = null

    private var historyCache = mutableListOf<History>()
    private var historyRepository = FakeHistoryRepository(FakeHistoryLocalDataSource(historyCache))
    private var alarmCache = mutableListOf<Alarm>()
    private var alarmRepo: AlarmRepository = FakeAlarmRepository(alarmCache)
    private var reminderCache = mutableListOf<Reminder>()
    private var reminderRepository: ReminderRepository = FakeReminderRepository(
        reminderCache, alarmCache, historyCache
    )


    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        hiltAndroidRule.inject()
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
        val alarm =
            Alarm(1, reminderId = reminder.id, triggerAt = currentLocalDateTime().plusHours(1))

        scheduleAlarmForReminder(context, alarm, reminder)

        intent = getCreatedIntentForAlarmAction(context, ACTION_ALARM_START, reminder.id)

        assertThat(intent).isNotNull()
    }

    @Test
    fun scheduleNextAlarm_populatedDb_setsAlarmForNextSoonest() =
        runBlockingTest {
            val now = currentLocalDateTime()

            val reminders = (1..2).map { Reminder() }

            // Save alarms with descending id but ascending time
            val alarms = reminders.mapIndexed { i, reminder ->
                Alarm(
                    reminderId = reminder.id,
                    triggerAt = now.plusMinutes(10L * (i + 1))
                )
            }


            reminderRepository.saveAll(reminders)
            alarms.forEach { runBlocking { alarmRepo.save(it) } }
            assertThat(scheduleNextAlarm(context, alarmRepo, reminderRepository)).isTrue()

            intent = getCreatedIntentForAlarmAction(context, ACTION_ALARM_START, reminders[0].id)

            assertThat(intent).isNotNull()
            assertThat(alarmRepo.getFirstScheduledAlarm()!!.copy(id = 0)).isEqualTo(
                alarms[0].copy(
                    scheduled = true
                )
            )

        }

    @Test
    fun scheduleNextAlarm_clearsPreviouslyScheduled() = runBlockingTest {
        val now = currentLocalDateTime()

        val reminders = (1..4).map { Reminder() }

        // Save alarms with descending id but ascending time
        val alarms = reminders.mapIndexed { i, reminder ->
            Alarm(
                reminderId = reminder.id, triggerAt = now.plusMinutes(i.toLong()),
                scheduled = if (i == 4) true else null
            )
        }


        reminderRepository.saveAll(reminders)
        alarms.forEach { runBlocking { alarmRepo.save(it) } }

        assertThat(scheduleNextAlarm(context, alarmRepo, reminderRepository)).isTrue()

        assertThat(alarmRepo.findById(4)!!.scheduled).isNull()
    }

    @Test
    fun scheduleNextAlarm_noAlarms_doesNotSchedule() = runBlockingTest {
        val reminder = Reminder()

        reminderRepository.save(reminder)
        scheduleNextAlarm(context, alarmRepo, reminderRepository)


        mockkStatic(::scheduleAlarmForReminder)
        justRun { scheduleAlarmForReminder(context, any(), any()) }

        verify(exactly = 0) {
            scheduleAlarmForReminder(context, any(), any())
        }
    }

    @Test
    fun rescheduleAlarms_savesNewAlarms_schedulesNext() = runBlockingTest {
        mockkStatic(::enqueueScheduleNext)
        justRun { enqueueScheduleNext(any(), any()) }

        val reminders = (1L..4).map {
            Reminder(
                schedulingRules = SchedulingRules(
                    time = currentLocalTime().plusMinutes(it)
                )
            )
        }

        reminderRepository.saveAll(reminders)

        val numRescheduled = rescheduleReminders(context, reminderRepository, alarmRepo, historyRepository)

        val alarms = alarmRepo.getAll().first()
        assertThat(alarms).hasSize(reminders.size)
        assertThat(numRescheduled).isEqualTo(reminders.size)
        verify {
            enqueueScheduleNext(any(), any())
        }
    }


    @Test
    fun rescheduleAlarms_doesNotSaveRemindersWithHistoryOnSameDay() = runBlockingTest {
        mockkStatic(::enqueueScheduleNext)
        justRun { enqueueScheduleNext(any(), any()) }

        val reminders = (1L..4).map {
            Reminder(
                schedulingRules = SchedulingRules(
                    type = SchedulingType.REPEAT_FOREVER,
                    daysOfWeek = DayOfWeek.values().toSortedSet()
                )
            )
        }

        // Save 2 history, one pending one completed. Pending should be allowed, but not completed.
        historyRepository.saveAll(
            listOf(
                History(
                    reminderId = reminders[0].id,
                    completionStatus = ReminderStatus.COMPLETED,
                    completedAt = currentEpochMilli()
                ),
                History(
                    reminderId = reminders[1].id,
                    completionStatus = ReminderStatus.PENDING,
                    completedAt = currentEpochMilli()
                )
            )
        )

        reminderRepository.saveAll(reminders)

        val numRescheduled = rescheduleReminders(context, reminderRepository, alarmRepo, historyRepository)

        val alarms = alarmRepo.getAll().first()
        assertThat(alarms).hasSize(reminders.size)
        assertThat(numRescheduled).isEqualTo(reminders.size)

        // The reminder with history COMPLETED should have an alarm saved for tomorrow
        assertThat(alarms[0].triggerAt.toLocalDate().dayOfWeek).isEqualTo(
            LocalDate.now().plusDays(1).dayOfWeek
        )

        // The reminders with pending or missing history should have alarms saved for today
        (1 until reminders.size).forEach { i ->
            assertThat(alarms[i].triggerAt.toLocalDate().dayOfWeek).isEqualTo(
                LocalDate.now().dayOfWeek
            )
        }

        verify {
            enqueueScheduleNext(any(), any())
        }
    }

    @Test
    fun rescheduleAlarms_doesNotSaveAlarmsInPast_ifHasHistory() = runBlockingTest {
        mockkStatic(::enqueueScheduleNext)
        justRun { enqueueScheduleNext(any(), any()) }

        val reminders = (1L..4).map {
            Reminder(
                schedulingRules = SchedulingRules(
                    time = currentLocalTime().plusMinutes(it),
                    type = SchedulingType.REPEAT_FOREVER,
                    daysOfWeek = DayOfWeek.values().toSortedSet()
                )
            )
        }.toMutableList()

        // Save one in past that will have a history with it
        reminders.add(
            Reminder(
                schedulingRules = SchedulingRules(
                    type = SchedulingType.SINGLE,
                    time = currentLocalTime().minusMinutes(10)
                )
            )
        )
        // Save history as not PENDING so it doesn't get a new time
        historyRepository.save(
            History(
                reminderId = reminders.last().id,
                completionStatus = ReminderStatus.COMPLETED,
                completedAt = Instant.now().toEpochMilli()
            )
        )

        reminderRepository.saveAll(reminders)

        val numRescheduled = rescheduleReminders(context, reminderRepository, alarmRepo, historyRepository)

        val alarms = alarmRepo.getAll().first()

        assertThat(alarms).hasSize(reminders.size - 1)
        assertThat(numRescheduled).isEqualTo(reminders.size - 1)
        verify {
            enqueueScheduleNext(any(), any())
        }
    }


    @Test
    fun rescheduleAlarms_doesSaveAlarmsInPast_ifNoHistory() = runBlockingTest {
        mockkStatic(::enqueueScheduleNext)
        justRun { enqueueScheduleNext(any(), any()) }

        // save a bunch of single in past
        val reminders = (1L..4).map {
            Reminder(
                schedulingRules = SchedulingRules(
                    time = currentLocalTime().minusMinutes(it * 10),
                    type = SchedulingType.SINGLE,
                )
            )
        }.toMutableList()
        reminderRepository.saveAll(reminders)

        val numRescheduled = rescheduleReminders(context, reminderRepository, alarmRepo, historyRepository)

        val alarms = alarmRepo.getAll().first()

        assertThat(alarms).hasSize(reminders.size)
        assertThat(numRescheduled).isEqualTo(reminders.size)
        verify {
            enqueueScheduleNext(any(), any())
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

        assertThat(
            getCreatedIntentForAlarmAction(
                context,
                ACTION_ALARM_SNOOZE,
                reminder.id
            )
        ).isNotNull()
        assertThat(
            getCreatedIntentForAlarmAction(
                context,
                ACTION_ALARM_SKIP,
                reminder.id
            )
        ).isNotNull()
        assertThat(
            getCreatedIntentForAlarmAction(
                context,
                ACTION_ALARM_COMPLETE,
                reminder.id
            )
        ).isNotNull()
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