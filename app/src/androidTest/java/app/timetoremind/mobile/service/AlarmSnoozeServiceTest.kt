package app.timetoremind.mobile.service

import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.test.rule.ServiceTestRule
import app.timetoremind.mobile.broadcast.ACTION_ALARM_SNOOZE
import app.timetoremind.mobile.broadcast.EXTRA_REMINDER
import app.timetoremind.mobile.broadcast.showSkipNotification
import app.timetoremind.mobile.broadcast.showSnoozeNotification
import app.timetoremind.mobile.data.AlarmRepository
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.data.domain.*
import app.timetoremind.mobile.data.domain.parcel.ReminderParcel
import app.timetoremind.mobile.util.currentLocalDateTime
import app.timetoremind.sharedtest.MainCoroutineRule
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.justRun
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.properties.Delegates

@ExperimentalCoroutinesApi
@HiltAndroidTest
@MediumTest
@SdkSuppress(minSdkVersion = 28)
class AlarmSnoozeServiceTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val serviceTestRule = ServiceTestRule()

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var binder: IBinder
    private var startCommand by Delegates.notNull<Int>()

    @Before
    fun setup() {
        hiltRule.inject()
        startCommand = 0
        mockkObject(AlarmSchedulingService.Companion)
        justRun { AlarmSchedulingService.startScheduleNext(any()) }
    }

    private fun createService(): AlarmSnoozeService {
        val intent = Intent(context, AlarmSnoozeService::class.java)
        binder = serviceTestRule.bindService(intent)
        return (binder as ReminderService.Binder).getService()!!
    }

    private fun AlarmSnoozeService.doOnStartCommand(reminder: Reminder) {
        val intent = Intent(context, AlarmSnoozeService::class.java).apply {
            action = ACTION_ALARM_SNOOZE
            putExtra(EXTRA_REMINDER, ReminderParcel(reminder))
        }
        onStartCommand(intent, 0, startCommand++)
    }

    @Test
    fun ifAlarmCanSnooze_savesNewAlarmWithNewTime() = runTest {
        mockkStatic(::showSnoozeNotification)
        justRun { showSnoozeNotification(any(), any(), any()) }

        val reminder = Reminder()
        reminderRepository.save(reminder)
        val alarm = Alarm(
            reminderId = reminder.id,
            snoozeCount = 0,
            triggerAt = currentLocalDateTime(),
            snoozeDuration = 5
        )
        alarmRepository.save(alarm)

        val service = createService()
        service.doOnStartCommand(reminder)

        val savedAlarm = alarmRepository.findByReminderId(reminder.id)
        assertThat(savedAlarm).isNotNull()
        assertThat(savedAlarm!!.triggerAt).isAtLeast(currentLocalDateTime().plusMinutes(alarm.snoozeDuration))
        verify {
            showSnoozeNotification(any(), reminder, savedAlarm)
            AlarmSchedulingService.startScheduleNext(any())
        }
    }

    @Test
    fun ifAlarmCantSnooze_savesSkipHistoryAndNoAlarm() = runTest {
        mockkStatic(::showSnoozeNotification, ::showSkipNotification)
        justRun { showSnoozeNotification(any(), any(), any()) }
        justRun { showSkipNotification(any(), any(), any()) }
        mockkObject(AlarmHistoryService.Companion)

        val reminder = Reminder(schedulingRules = SchedulingRules(type = SchedulingType.SINGLE))
        reminderRepository.save(reminder)
        val alarm = Alarm(
            reminderId = reminder.id,
            snoozeCount = 3,
            triggerAt = currentLocalDateTime(),
            snoozeDuration = 5,
            maxSnoozeRepeats = 1
        )
        alarmRepository.save(alarm)

        val service = createService()
        service.doOnStartCommand(reminder)

        // Doesnt save a new alarm
        val savedAlarm = alarmRepository.findByReminderId(reminder.id)
        assertThat(savedAlarm).isNull()

        verify(exactly = 0) {
            showSnoozeNotification(any(), reminder, any())
        }
        verify {
            AlarmHistoryService.start(any(), reminder, ReminderStatus.SKIPPED)
            showSkipNotification(any(), reminder, any())
            AlarmSchedulingService.startScheduleNext(any())
        }
    }

    @Test
    fun ifNonRepeatingReminderCantSnooze_deletesReminder() = runTest {
        mockkStatic(::showSnoozeNotification, ::showSkipNotification)
        justRun { showSnoozeNotification(any(), any(), any()) }
        justRun { showSkipNotification(any(), any(), any()) }
        mockkObject(AlarmHistoryService.Companion)

        val reminder = Reminder(schedulingRules = SchedulingRules(type = SchedulingType.SINGLE))
        reminderRepository.save(reminder)
        val alarm = Alarm(
            reminderId = reminder.id,
            snoozeCount = 3,
            triggerAt = currentLocalDateTime(),
            snoozeDuration = 5,
            maxSnoozeRepeats = 1
        )
        alarmRepository.save(alarm)

        val service = createService()
        service.doOnStartCommand(reminder)

        // Deletes reminder
        val savedReminder = reminderRepository.findById(reminder.id)
        assertThat(savedReminder).isNull()
    }

    @Test
    fun multipleStarts_allGetResolved() = runTest {
        mockkStatic(::showSnoozeNotification)
        justRun { showSnoozeNotification(any(), any(), any()) }

        val service = createService()

        val reminders = listOf(Reminder(), Reminder(), Reminder())
        reminderRepository.saveAll(reminders)

        assertThat(service.isCreated).isTrue()
        reminders.forEach { reminder ->
            val alarm = Alarm(
                reminderId = reminder.id,
                snoozeCount = 0,
                triggerAt = currentLocalDateTime(),
                snoozeDuration = 5
            )
            alarmRepository.save(alarm)

            // Start service 3 times, should call all methods 3 times
            service.doOnStartCommand(reminder)
        }

        assertThat(service.totalStartCommands).isEqualTo(reminders.size)
        assertThat(service.startCommands).isEmpty()

        val alarms = alarmRepository.getAll().first()
        assertThat(alarms).hasSize(reminders.size)

        reminders.forEach {
            verify {
                showSnoozeNotification(any(), it, any())
            }
        }
        verify(exactly = reminders.size) {
            AlarmSchedulingService.startScheduleNext(any())
        }
    }
}