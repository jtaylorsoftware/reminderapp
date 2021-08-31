package com.github.jeremyt135.reminderapp.worker

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import androidx.work.ListenableWorker.Result
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.broadcast.*
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.*
import com.github.jeremyt135.reminderapp.util.currentEpochMilli
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import com.github.jeremyt135.reminderapp.work.AlarmActionHandlerImpl
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class AlarmActionHandlerTest {
    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @MockK
    lateinit var mockReminderRepo: ReminderRepository

    @MockK
    lateinit var mockAlarmRepository: AlarmRepository

    @MockK
    lateinit var mockHistoryRepository: HistoryRepository

    @MockK
    lateinit var mockContext: Context

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val mockWorkManager = mockkClass(WorkManager::class)
        every { mockWorkManager.enqueue(any<WorkRequest>()) } returns mockkClass(Operation::class)
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns mockWorkManager

        mockkObject(AlarmActionHandler.Companion)
        every {
            AlarmActionHandler.createForAction(any(), any())
        } returns OneTimeWorkRequestBuilder<AlarmActionHandler>().build()
    }

    private fun createWorker(inputData: Data = workDataOf()): AlarmActionHandlerImpl {
        return AlarmActionHandlerImpl(
            mockContext,
            inputData,
            mockAlarmRepository,
            mockReminderRepo,
            mockHistoryRepository
        )
    }

    @Test
    fun actionIsNull_returnsFailure() {
        val worker = createWorker()
        runBlocking {
            val result = worker.doWork()
            assertThat(result).isEqualTo(Result.failure())
        }
    }

    @Test
    fun actionIsNotExpected_returnsFailure() {
        val worker = createWorker(
            workDataOf(
                EXTRA_ALARM_ACTION_NAME to ACTION_ALARM_START
            )
        )
        runBlocking {
            val result = worker.doWork()
            assertThat(result).isEqualTo(Result.failure())
        }
    }

    @Test
    fun actionSnooze_snoozesAlarm_showsNotification() {
        val reminder = Reminder()
        val alarm = Alarm(
            id = 1,
            reminderId = reminder.id,
            triggerAt = currentLocalDateTime(),
            maxSnoozeRepeats = 3
        )
        val nextAlarm = slot<Alarm>()

        coEvery { mockReminderRepo.findById(reminder.id) } returns reminder
        coEvery { mockAlarmRepository.findByReminderId(reminder.id) } returns alarm
        coJustRun { mockAlarmRepository.delete(alarm) }
        coEvery { mockAlarmRepository.save(capture(nextAlarm)) } returns 2L

        mockkStatic(::showSnoozeNotification, ::scheduleNextAlarm)
        mockkStatic(NotificationManagerCompat::class)
        val notifManager = mockkClass(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(any()) } returns notifManager
        justRun { notifManager.cancel(any()) }
        justRun { showSnoozeNotification(any(), any(), any()) }

        val worker = createWorker(
            workDataOf(
                EXTRA_ALARM_ACTION_NAME to ACTION_ALARM_SNOOZE,
                EXTRA_REMINDER_ID to reminder.id
            )
        )
        runBlocking {
            val result = worker.doWork()
            assertThat(result).isEqualTo(Result.success())
        }

        coVerify {
            mockReminderRepo.findById(reminder.id)
            mockAlarmRepository.findByReminderId(reminder.id)
            mockAlarmRepository.save(any())
            NotificationManagerCompat.from(any())
            notifManager.cancel(alarm.notificationId)
            showSnoozeNotification(any(), reminder, any())
            mockAlarmRepository.delete(alarm)
        }

        assertThat(nextAlarm.captured.reminderId).isEqualTo(reminder.id)
        assertThat(nextAlarm.captured.triggerAt).isEqualTo(
            alarm.triggerAt.plusMinutes(alarm.snoozeDuration)
        )
        assertThat(nextAlarm.captured.snoozeCount).isEqualTo(1)
    }

    @Test
    fun actionSnooze_alarmNeverSnoozes_returnsFailure() {
        val reminder = Reminder()
        val alarm = Alarm(
            id = 1,
            reminderId = reminder.id,
            triggerAt = currentLocalDateTime(),
            maxSnoozeRepeats = 0
        )

        coEvery { mockReminderRepo.findById(reminder.id) } returns reminder
        coEvery { mockAlarmRepository.findByReminderId(reminder.id) } returns alarm
        coJustRun { mockAlarmRepository.delete(alarm) }

        mockkStatic(::showSnoozeNotification, ::scheduleNextAlarm, ::showSkipNotification)
        mockkStatic(NotificationManagerCompat::class)
        val notifManager = mockkClass(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(any()) } returns notifManager
        justRun { showSkipNotification(any(), any(), any()) }
        justRun { notifManager.cancel(any()) }

        val worker = createWorker(
            workDataOf(
                EXTRA_ALARM_ACTION_NAME to ACTION_ALARM_SNOOZE,
                EXTRA_REMINDER_ID to reminder.id
            )
        )
        runBlocking {
            val result = worker.doWork()
            assertThat(result).isEqualTo(Result.failure())
        }

        coVerify {
            mockReminderRepo.findById(reminder.id)
            mockAlarmRepository.findByReminderId(reminder.id)
            mockAlarmRepository.delete(alarm)
            NotificationManagerCompat.from(any())
            notifManager.cancel(alarm.notificationId)
        }
        coVerify(exactly = 0) {
            mockAlarmRepository.save(any())
            showSnoozeNotification(any(), reminder, any())
        }
    }

    @Test
    fun actionSnooze_alarmCannotBeSnoozedMore_returnsFailure() {
        val reminder = Reminder()
        val alarm = Alarm(
            id = 1,
            reminderId = reminder.id,
            triggerAt = currentLocalDateTime(),
            maxSnoozeRepeats = 1,
            snoozeCount = 1
        )

        coEvery { mockReminderRepo.findById(reminder.id) } returns reminder
        coEvery { mockAlarmRepository.findByReminderId(reminder.id) } returns alarm
        coJustRun { mockAlarmRepository.delete(alarm) }

        mockkStatic(::showSnoozeNotification, ::scheduleNextAlarm, ::showSkipNotification)
        mockkStatic(NotificationManagerCompat::class)
        val notifManager = mockkClass(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(any()) } returns notifManager
        justRun { showSkipNotification(any(), any(), any()) }
        justRun { notifManager.cancel(any()) }

        val worker = createWorker(
            workDataOf(
                EXTRA_ALARM_ACTION_NAME to ACTION_ALARM_SNOOZE,
                EXTRA_REMINDER_ID to reminder.id
            )
        )
        runBlocking {
            val result = worker.doWork()
            assertThat(result).isEqualTo(Result.failure())
        }

        coVerify {
            mockReminderRepo.findById(reminder.id)
            mockAlarmRepository.findByReminderId(reminder.id)
            mockAlarmRepository.delete(alarm)
            NotificationManagerCompat.from(any())
            notifManager.cancel(alarm.notificationId)
        }
        coVerify(exactly = 0) {
            mockAlarmRepository.save(any())
            showSnoozeNotification(any(), reminder, any())
        }
    }


    @Test
    fun actionSkip_savesHistoryAsSkip_schedulesNext() {
        val reminder = Reminder(daysOfWeek = sortedSetOf(*DayOfWeek.values()))
        val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = currentLocalDateTime())
        val history = slot<History>()
        val nextAlarm = slot<Alarm>()
        coEvery { mockReminderRepo.findById(reminder.id) } returns reminder
        coEvery { mockAlarmRepository.findByReminderId(reminder.id) } returns alarm
        coJustRun { mockAlarmRepository.delete(alarm) }
        coJustRun { mockHistoryRepository.save(capture(history)) }
        coEvery { mockAlarmRepository.save(capture(nextAlarm)) } returns 2L

        mockkStatic(::scheduleNextAlarm)
        coEvery { scheduleNextAlarm(any(), any(), any()) } returns true

        val worker = createWorker(
            workDataOf(
                EXTRA_ALARM_ACTION_NAME to ACTION_ALARM_SKIP,
                EXTRA_REMINDER_ID to reminder.id
            )
        )
        runBlocking {
            val result = worker.doWork()
            assertThat(result).isEqualTo(Result.success())
        }

        coVerify {
            mockReminderRepo.findById(reminder.id)
            mockAlarmRepository.findByReminderId(reminder.id)
            mockHistoryRepository.save(any())
            mockAlarmRepository.save(any())
            mockAlarmRepository.delete(alarm)
        }

        assertThat(history.captured.copy(id = "")).isEqualTo(
            History(
                id = "",
                reminderId = reminder.id,
                description = reminder.description,
                completionStatus = ReminderStatus.SKIPPED,
                completedAt = currentEpochMilli()
            )
        )

        assertThat(nextAlarm.captured.reminderId).isEqualTo(reminder.id)
        assertThat(nextAlarm.captured.triggerAt).isEqualTo(
            reminder.getNextTimeForAlarm(
                LocalDate.now().dayOfWeek,
                false
            )!!
        )
    }

    @Test
    fun actionComplete_savesHistoryAsComplete_schedulesNext() {
        val reminder = Reminder(daysOfWeek = sortedSetOf(*DayOfWeek.values()))
        val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = currentLocalDateTime())
        val history = slot<History>()
        val nextAlarm = slot<Alarm>()
        coEvery { mockReminderRepo.findById(reminder.id) } returns reminder
        coEvery { mockAlarmRepository.findByReminderId(reminder.id) } returns alarm
        coJustRun { mockAlarmRepository.delete(alarm) }
        coJustRun { mockHistoryRepository.save(capture(history)) }
        coEvery { mockAlarmRepository.save(capture(nextAlarm)) } returns 2L

        mockkStatic(::scheduleNextAlarm)
        coEvery { scheduleNextAlarm(any(), any(), any()) } returns true

        val worker = createWorker(
            workDataOf(
                EXTRA_ALARM_ACTION_NAME to ACTION_ALARM_COMPLETE,
                EXTRA_REMINDER_ID to reminder.id
            )
        )
        runBlocking {
            val result = worker.doWork()
            assertThat(result).isEqualTo(Result.success())
        }

        coVerify {
            mockReminderRepo.findById(reminder.id)
            mockAlarmRepository.findByReminderId(reminder.id)
            mockHistoryRepository.save(any())
            mockAlarmRepository.save(any())
            mockAlarmRepository.delete(alarm)
        }

        assertThat(history.captured.copy(id = "")).isEqualTo(
            History(
                id = "",
                reminderId = reminder.id,
                description = reminder.description,
                completionStatus = ReminderStatus.COMPLETED,
                completedAt = currentEpochMilli()
            )
        )

        assertThat(nextAlarm.captured.reminderId).isEqualTo(reminder.id)
        assertThat(nextAlarm.captured.triggerAt).isEqualTo(
            reminder.getNextTimeForAlarm(
                LocalDate.now().dayOfWeek,
                false
            )!!
        )
    }
}