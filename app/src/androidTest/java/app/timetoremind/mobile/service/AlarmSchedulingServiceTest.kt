package app.timetoremind.mobile.service

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.test.rule.ServiceTestRule
import app.timetoremind.mobile.MainCoroutineRule
import app.timetoremind.mobile.broadcast.*
import app.timetoremind.mobile.data.AlarmRepository
import app.timetoremind.mobile.data.HistoryRepository
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.data.domain.*
import app.timetoremind.mobile.data.domain.parcel.ReminderParcel
import app.timetoremind.mobile.util.currentEpochMilli
import app.timetoremind.mobile.util.currentLocalDateTime
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlin.properties.Delegates

@ExperimentalCoroutinesApi
@HiltAndroidTest
@MediumTest
@SdkSuppress(minSdkVersion = 28)
class AlarmSchedulingServiceTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val serviceTestRule = ServiceTestRule()

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var historyRepository: HistoryRepository

    @Inject
    lateinit var testScope: MainCoroutineRule

    private lateinit var reminders: List<Reminder>

    val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var binder: IBinder
    private var startCommand by Delegates.notNull<Int>()

    private lateinit var alarmManager: AlarmManager

    @Before
    fun setup() {
        hiltRule.inject()

        startCommand = 0

        mockkObject(AlarmSchedulingService)
        justRun { AlarmSchedulingService.startScheduleNext(any()) }

        reminders = listOf(
            Reminder(
                schedulingRules = SchedulingRules(
                    time = LocalTime.now().plusMinutes(5)
                )
            ), Reminder(
                schedulingRules = SchedulingRules(
                    time = LocalTime.now().plusMinutes(10)
                )
            ), Reminder(
                schedulingRules = SchedulingRules(
                    time = LocalTime.now().plusMinutes(15)
                )
            )
        )
        runBlocking { reminderRepository.saveAll(reminders) }

        mockkStatic(::cancelAlarmIntentsForReminder)
        justRun { cancelAlarmIntentsForReminder(any(), any()) }

        mockkStatic(::createAlarmActionIntent)
        justRun { createAlarmActionIntent(any(), any(), any()) }
    }

    private fun createService(): AlarmSchedulingService {
        val intent = Intent(context, AlarmSchedulingService::class.java)
        binder = serviceTestRule.bindService(intent)
        val service =
            spyk((binder as ReminderService.Binder).getService<AlarmSchedulingService>()!!)
        alarmManager = mockkClass(
            AlarmManager::class,
            relaxed = true
        )
        every { service.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
        justRun { alarmManager.setAlarmClock(any(), any()) }

        return service
    }

    private fun AlarmSchedulingService.doScheduleNewAlarm(reminder: Reminder) {
        val intent = Intent(context, AlarmSchedulingService::class.java).apply {
            action = ACTION_ALARM_SAVE_NEW
            putExtra(EXTRA_REMINDER, ReminderParcel(reminder))
        }
        onStartCommand(intent, 0, startCommand++)
    }

    private fun AlarmSchedulingService.doScheduleNext() {
        val intent = Intent(context, AlarmSchedulingService::class.java).apply {
            action = ACTION_ALARM_SCHEDULE_NEXT
        }
        onStartCommand(intent, 0, startCommand++)
    }

    private fun AlarmSchedulingService.doRescheduleAll() {
        val intent = Intent(context, AlarmSchedulingService::class.java).apply {
            action = ACTION_ALARM_RESCHEDULE_ALL
        }
        onStartCommand(intent, 0, startCommand++)
    }

    @Test
    fun saveNewAlarm_noHistoryToday_createsAlarmWithTimeToday() =
        testScope.runBlockingTest {
            val reminder =
                Reminder(schedulingRules = SchedulingRules(time = LocalTime.now().plusMinutes(10)))
            reminderRepository.save(reminder)
            val alarm = Alarm(
                reminderId = reminder.id,
                snoozeCount = 0,
                triggerAt = currentLocalDateTime(),
                snoozeDuration = 5
            )
            // save an old alarm to be deleted
            alarmRepository.save(alarm)

            val service = createService()
            service.doScheduleNewAlarm(reminder)

            val savedAlarm = alarmRepository.findByReminderId(reminder.id)
            assertThat(savedAlarm!!.id).isNotEqualTo(alarm.id)
            assertThat(savedAlarm.triggerAt.toLocalDate()).isEqualTo(LocalDate.now())
            assertThat(savedAlarm.triggerAt).isEqualTo(
                reminder.schedulingRules.getNextTimeForAlarm(
                    currentLocalDateTime(),
                    nextTimeOptions = SchedulingRules.NextTimeOptions.ALLOW_ALL
                )
            )
        }

    @Test
    fun saveNewAlarm_hasHistoryForToday_createsAlarmWithNextScheduledTime() =
        testScope.runBlockingTest {
            val reminder = Reminder(
                schedulingRules = SchedulingRules(
                    type = SchedulingType.REPEATING,
                    endDate = LocalDate.now().plusDays(7),
                    daysOfWeek = sortedSetOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                    time = LocalTime.now().plusMinutes(10)
                )
            )
            reminderRepository.save(reminder)
            val alarm = Alarm(
                reminderId = reminder.id,
                snoozeCount = 0,
                triggerAt = currentLocalDateTime(),
                snoozeDuration = 5
            )
            // save an old alarm to be deleted
            alarmRepository.save(alarm)

            // save a history entry
            historyRepository.save(
                History(
                    reminderId = reminder.id,
                    completedAt = currentEpochMilli(),
                    completionStatus = ReminderStatus.COMPLETED
                )
            )

            val service = createService()
            service.doScheduleNewAlarm(reminder)

            // should not be scheduled for today
            val savedAlarm = alarmRepository.findByReminderId(reminder.id)
            assertThat(savedAlarm!!.id).isNotEqualTo(alarm.id)
            assertThat(savedAlarm.triggerAt.toLocalDate()).isNotEqualTo(LocalDate.now())
        }

    @Test
    fun saveNewAlarm_alsoCallsScheduleNext() =
        testScope.runBlockingTest {
            val reminder =
                Reminder(schedulingRules = SchedulingRules(time = LocalTime.now().plusMinutes(10)))
            reminderRepository.save(reminder)

            val service = createService()
            service.doScheduleNewAlarm(reminder)

            verify {
                AlarmSchedulingService.startScheduleNext(any())
            }
        }

    @Test
    fun scheduleNext_noPreviousScheduled_setsOneAlarmAsScheduled() = testScope.runBlockingTest {
        reminders.forEach {
            alarmRepository.save(
                Alarm(
                    reminderId = it.id,
                    triggerAt = it.schedulingRules.getNextTimeForAlarm(
                        currentLocalDateTime(),
                        nextTimeOptions = SchedulingRules.NextTimeOptions.ALLOW_ALL
                    )!!
                )
            )
        }

        val service = createService()
        service.doScheduleNext()

        val alarms = alarmRepository.getAll().first()
        val numScheduled = alarms.count { it.scheduled == true }
        assertThat(numScheduled).isEqualTo(1)
        val scheduled = alarmRepository.getFirstScheduledAlarm()!!
        val minTimeAlarm = alarms.minByOrNull { it.triggerAt }!!
        assertThat(scheduled).isEqualTo(minTimeAlarm)

        verify(exactly = 1) {
            createAlarmActionIntent(any(), ACTION_ALARM_START, scheduled.reminderId)
        }
    }

    @Test
    fun scheduleNext_alarmAlreadyScheduledNotInPast_doesNothing() = testScope.runBlockingTest {
        reminders.forEachIndexed { i, reminder ->
            alarmRepository.save(
                Alarm(
                    reminderId = reminder.id,
                    scheduled = if (i == 0) true else null,
                    triggerAt = reminder.schedulingRules.getNextTimeForAlarm(
                        currentLocalDateTime(),
                        nextTimeOptions = SchedulingRules.NextTimeOptions.ALLOW_ALL
                    )!!
                )
            )
        }

        val service = createService()
        service.doScheduleNext()

        verify(exactly = 0) {
            cancelAlarmIntentsForReminder(any(), any())
            createAlarmActionIntent(any(), ACTION_ALARM_START, any())
        }
    }

    @Test
    fun scheduleNext_alarmAlreadyScheduledTimeIsInPast_reschedules() = testScope.runBlockingTest {
        reminders.forEachIndexed { i, reminder ->
            alarmRepository.save(
                Alarm(
                    reminderId = reminder.id,
                    scheduled = if (i == 0) true else null,
                    triggerAt = if (i == 0) currentLocalDateTime().minusMinutes(10) else {
                        reminder.schedulingRules.getNextTimeForAlarm(
                            currentLocalDateTime(),
                            nextTimeOptions = SchedulingRules.NextTimeOptions.ALLOW_ALL
                        )!!
                    }
                )
            )
        }

        val previousAlarm = alarmRepository.findByReminderId(reminders[0].id)!!

        val service = createService()
        service.doScheduleNext()

        val scheduled = alarmRepository.getFirstScheduledAlarm()!!
        assertThat(previousAlarm.triggerAt).isNotEqualTo(scheduled.triggerAt)
        assertThat(scheduled.triggerAt).isAtLeast(currentLocalDateTime().plusMinutes(1))
        val scheduledReminder = reminders.find { it.id == scheduled.reminderId }!!
        assertThat(scheduledReminder).isEqualTo(reminders[0])

        verify(exactly = 1) {
            cancelAlarmIntentsForReminder(any(), scheduledReminder)
            createAlarmActionIntent(any(), ACTION_ALARM_START, scheduledReminder.id)
        }
    }

    @Test
    fun scheduleNext_scheduledAlarmNotSameAsSoonest_reschedules() = testScope.runBlockingTest {
        reminders.forEachIndexed { i, reminder ->
            alarmRepository.save(
                Alarm(
                    reminderId = reminder.id,
                    scheduled = if (i == 1) true else null,
                    triggerAt = reminder.schedulingRules.getNextTimeForAlarm(
                        currentLocalDateTime(),
                        nextTimeOptions = SchedulingRules.NextTimeOptions.ALLOW_ALL
                    )!!
                )
            )
        }

        val previouslyScheduled = alarmRepository.getFirstScheduledAlarm()!!
        val previousReminder = reminders[1]

        val service = createService()
        service.doScheduleNext()

        val scheduled = alarmRepository.getFirstScheduledAlarm()!!
        assertThat(scheduled.triggerAt).isAtLeast(currentLocalDateTime().plusMinutes(1))
        assertThat(scheduled).isNotEqualTo(previouslyScheduled)

        verify(exactly = 1) {
            cancelAlarmIntentsForReminder(any(), previousReminder)
            createAlarmActionIntent(any(), ACTION_ALARM_START, scheduled.reminderId)
        }
    }

    @Test
    fun scheduleNext_handlesMultipleStarts() = testScope.runBlockingTest {
        val service = createService()

        reminders.forEach {
            service.doScheduleNext()
        }

        assertThat(service.totalStartCommands).isEqualTo(reminders.size)
        assertThat(service.startCommands).isEmpty()
        assertThat(service.pendingScheduleNextCommands).isEmpty()

        coVerify(exactly = reminders.size) {
            alarmRepository.getFirstByTimeAscending()
        }
    }

    @Test
    fun reschedule_callsUtilRescheduleAll() = testScope.runBlockingTest {
        val service = createService()
        service.doRescheduleAll()

        coVerify {
            service.rescheduleReminders()
        }
    }
}