package app.timetoremind.mobile.broadcast

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import app.timetoremind.mobile.data.AlarmRepository
import app.timetoremind.mobile.data.HistoryRepository
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.data.domain.*
import app.timetoremind.mobile.service.AlarmSchedulingService
import app.timetoremind.mobile.util.currentEpochMilli
import app.timetoremind.mobile.util.currentLocalTime
import app.timetoremind.sharedtest.MainCoroutineRule
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.justRun
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@ExperimentalCoroutinesApi
@HiltAndroidTest
@SmallTest
@SdkSuppress(minSdkVersion = 28)
class AlarmUtilTest {
    @get:Rule
    var hiltAndroidRule = HiltAndroidRule(this)

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    lateinit var context: Context

    private var intent: PendingIntent? = null

    @Inject
    lateinit var historyRepository: HistoryRepository

    @Inject
    lateinit var alarmRepo: AlarmRepository

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        hiltAndroidRule.inject()

        mockkObject(AlarmSchedulingService.Companion)
        justRun { AlarmSchedulingService.startScheduleNext(any()) }
    }

    @After
    fun teardown() {
        intent?.cancel()
        intent = null
        NotificationManagerCompat.from(context).cancelAll()
    }

    @Test
    fun rescheduleAlarms_savesNewAlarms_schedulesNext() = runTest {
        val reminders = (1L..4).map {
            Reminder(
                schedulingRules = SchedulingRules(
                    time = currentLocalTime().plusMinutes(it)
                )
            )
        }

        reminderRepository.saveAll(reminders)

        val numRescheduled =
            rescheduleReminders(context, reminderRepository, alarmRepo, historyRepository)

        val alarms = alarmRepo.getAll().first()
        assertThat(alarms).hasSize(reminders.size)
        assertThat(numRescheduled.getOrNull()!!).isEqualTo(reminders.size)
        verify {
            AlarmSchedulingService.startScheduleNext(any())
        }
    }

    @Test
    fun rescheduleAlarms_savesExpiredSingleReminders() = runTest {
        // the base time for the test
        val t0 = currentLocalTime()
        val reminders = (1L..4).map {
            Reminder(
                schedulingRules = SchedulingRules(
                    time = if (it % 2 == 0L) t0.plusMinutes(it * 10) else t0.minusMinutes(it * 10),
                    type = SchedulingType.SINGLE,
                )
            )
        }

        reminderRepository.saveAll(reminders)

        val numRescheduled =
            rescheduleReminders(context, reminderRepository, alarmRepo, historyRepository)

        // Should save all the reminders
        val alarms = alarmRepo.getAll().first()
        assertThat(alarms).hasSize(reminders.size)
        assertThat(numRescheduled.getOrNull()!!).isEqualTo(reminders.size)

        // Should only save in the future if the time was in the past
        alarms.forEachIndexed { i, alarm ->
            val reminderTime = reminders[i].schedulingRules.time
            if (reminderTime.isAfter(t0)) {
                // In future, should be the same time
                assertThat(alarm.triggerAt).isEqualTo(LocalDate.now().atTime(reminderTime))
            } else {
                // In past, should be also in the future but not the same time
                assertThat(alarm.triggerAt).isNotEqualTo(reminderTime)
                assertThat(alarm.triggerAt.isAfter(LocalDate.now().atTime(t0))).isTrue()
            }
        }

        verify {
            AlarmSchedulingService.startScheduleNext(any())
        }
    }

    @Test
    fun rescheduleAlarms_doesNotSaveRemindersWithHistoryOnSameDay() = runTest {
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

        val numRescheduled =
            rescheduleReminders(context, reminderRepository, alarmRepo, historyRepository)

        val alarms = alarmRepo.getAll().first()
        assertThat(alarms).hasSize(reminders.size)
        assertThat(numRescheduled.getOrNull()!!).isEqualTo(reminders.size)

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
            AlarmSchedulingService.startScheduleNext(any())
        }
    }

    @Test
    fun rescheduleAlarms_doesNotSaveAlarmsInPast_ifHasHistory() = runTest {
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

        val numRescheduled =
            rescheduleReminders(context, reminderRepository, alarmRepo, historyRepository)

        val alarms = alarmRepo.getAll().first()

        assertThat(alarms).hasSize(reminders.size - 1)
        assertThat(numRescheduled.getOrNull()!!).isEqualTo(reminders.size - 1)
        verify {
            AlarmSchedulingService.startScheduleNext(any())
        }
    }


    @Test
    fun rescheduleAlarms_doesSaveAlarmsInPast_ifNoHistory() = runTest {
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

        val numRescheduled =
            rescheduleReminders(context, reminderRepository, alarmRepo, historyRepository)

        val alarms = alarmRepo.getAll().first()

        assertThat(alarms).hasSize(reminders.size)
        assertThat(numRescheduled.getOrNull()!!).isEqualTo(reminders.size)
        verify {
            AlarmSchedulingService.startScheduleNext(any())
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
            getCreatedAlarmStartIntent(
                context,
                ACTION_ALARM_SNOOZE,
                reminder.id
            )
        ).isNotNull()
        assertThat(
            getCreatedAlarmStartIntent(
                context,
                ACTION_ALARM_SKIP,
                reminder.id
            )
        ).isNotNull()
        assertThat(
            getCreatedAlarmStartIntent(
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