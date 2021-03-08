package com.github.jeremyt135.reminderapp.worker

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.SavedStateHandle
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.ReminderApplication
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
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class AlarmActionHandlerTest {
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @MockK
    lateinit var mockReminderRepo: ReminderRepository

    @MockK
    lateinit var mockAlarmRepository: AlarmRepository

    @MockK
    lateinit var mockHistoryRepository: HistoryRepository

    @MockK
    lateinit var mockSavedStateHandle: SavedStateHandle

    @MockK
    lateinit var mockApplication: ReminderApplication

    @MockK
    lateinit var mockContext: Context

    @MockK
    lateinit var mockWorkerParameters: WorkerParameters

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    fun createWorker(inputData: Data = workDataOf()): AlarmActionHandlerImpl {
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
        val reminder = Reminder(id = 1)
        val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = currentLocalDateTime())

        coEvery { mockReminderRepo.getReminderAndAlarm(reminder.id) } returns ReminderAndAlarm(
            reminder,
            alarm
        )
        coJustRun { mockAlarmRepository.delete(alarm) }
        coEvery { mockAlarmRepository.save(any()) } returns 2L

        mockkStatic(::triggerSnoozeNotification, ::scheduleNextAlarm)
        mockkStatic(NotificationManagerCompat::class)
        val notifManager = mockkClass(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(any()) } returns notifManager
        justRun { notifManager.cancel(any()) }
        coJustRun { scheduleNextAlarm(any(), any(), any()) }
        justRun { triggerSnoozeNotification(any(), any(), any()) }

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

        val nextAlarm = Alarm(
            id = 2,
            reminderId = reminder.id,
            triggerAt = alarm.triggerAt.plusMinutes(SNOOZE_TIME_MIN)
        )
        coVerify {
            mockReminderRepo.getReminderAndAlarm(reminder.id)
            mockAlarmRepository.save(nextAlarm)
            NotificationManagerCompat.from(any())
            notifManager.cancel(alarm.id.toInt())
            triggerSnoozeNotification(any(), reminder, nextAlarm)
            mockAlarmRepository.delete(alarm)
        }
    }

    @Test
    fun actionSkip_savesHistoryAsSkip_schedulesNext() {
        val reminder = Reminder(id = 1, daysOfWeek = sortedSetOf(*DayOfWeek.values()))
        val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = currentLocalDateTime())

        coEvery { mockReminderRepo.getReminderAndAlarm(reminder.id) } returns ReminderAndAlarm(
            reminder,
            alarm
        )
        coJustRun { mockAlarmRepository.delete(alarm) }
        coJustRun {  mockHistoryRepository.save(any()) }
        coEvery { mockAlarmRepository.save(any()) } returns 2L

        mockkStatic(::scheduleNextAlarm)
        coJustRun { scheduleNextAlarm(any(), any(), any()) }

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

        val nextAlarm = Alarm(
            id = 2,
            reminderId = reminder.id,
            triggerAt = reminder.getNextTimeForAlarm(LocalDate.now().dayOfWeek, false)!!
        )
        coVerify {
            mockReminderRepo.getReminderAndAlarm(reminder.id)
            mockHistoryRepository.save(
                History(
                    reminderId = reminder.id,
                    description = reminder.description,
                    completionStatus = ReminderStatus.SKIPPED,
                    completedAt = currentEpochMilli()
                )
            )
            mockAlarmRepository.save(nextAlarm)
            mockAlarmRepository.delete(alarm)
        }
    }

    @Test
    fun actionComplete_savesHistoryAsComplete_schedulesNext() {
        val reminder = Reminder(id = 1, daysOfWeek = sortedSetOf(*DayOfWeek.values()))
        val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = currentLocalDateTime())

        coEvery { mockReminderRepo.getReminderAndAlarm(reminder.id) } returns ReminderAndAlarm(
            reminder,
            alarm
        )
        coJustRun { mockAlarmRepository.delete(alarm) }
        coJustRun {  mockHistoryRepository.save(any()) }
        coEvery { mockAlarmRepository.save(any()) } returns 2L

        mockkStatic(::scheduleNextAlarm)
        coJustRun { scheduleNextAlarm(any(), any(), any()) }

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

        val nextAlarm = Alarm(
            id = 2,
            reminderId = reminder.id,
            triggerAt = reminder.getNextTimeForAlarm(LocalDate.now().dayOfWeek, false)!!
        )
        coVerify {
            mockReminderRepo.getReminderAndAlarm(reminder.id)
            mockHistoryRepository.save(
                History(
                    reminderId = reminder.id,
                    description = reminder.description,
                    completionStatus = ReminderStatus.COMPLETED,
                    completedAt = currentEpochMilli()
                )
            )
            mockAlarmRepository.save(nextAlarm)
            mockAlarmRepository.delete(alarm)
        }
    }
}