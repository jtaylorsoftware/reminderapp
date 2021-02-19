package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver.PendingResult
import android.content.Context
import android.content.Intent
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.util.toLocalDateTime
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@ExperimentalCoroutinesApi
class AlarmCompleteReceiverTest {
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @SpyK
    var receiver: AlarmCompleteReceiver = AlarmCompleteReceiver()

    @MockK
    lateinit var mockReminderRepository: ReminderRepository

    @MockK
    lateinit var mockAlarmRepository: AlarmRepository

    @MockK
    lateinit var mockHistoryRepository: HistoryRepository

    @MockK
    lateinit var mockContext: Context

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        receiver.reminderRepository = mockReminderRepository
        receiver.alarmRepository = mockAlarmRepository
        receiver.historyRepository = mockHistoryRepository

        val mockPendingResult = mockk<PendingResult>()
        every { receiver.goAsync() } returns mockPendingResult
        every { mockPendingResult.finish() } returns Unit
    }

    @Test
    fun intentAction_notAlarmComplete_returnsWithoutComplete() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_BOOT_COMPLETED

        receiver.onReceive(mockContext, intent)

        coVerify(exactly = 0){
            mockReminderRepository.findById(any())
        }
    }

    @Test
    fun intentAction_isAlarmComplete_marksReminderComplete() {
        val reminder = Reminder(id = 1)
        coEvery { mockReminderRepository.findById(reminder.id) } returns reminder
        val completeReminder = reminder.copy(status = ReminderStatus.COMPLETED)
        coEvery { mockReminderRepository.save(completeReminder) } returns reminder.id
        val savedHistory = slot<History>()
        coEvery { mockHistoryRepository.save(capture(savedHistory))} returns 1L

        mockkStatic(::cancelNotification)
        every { cancelNotification(mockContext, reminder.id) } returns Unit

        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_COMPLETE
        every { intent.getLongExtra(EXTRA_REMINDER_ID, 0) } returns reminder.id
        receiver.onReceive(mockContext,intent)

        coVerifyOrder {
            mockReminderRepository.findById(reminder.id)
            mockHistoryRepository.save(any())
            cancelNotification(mockContext, reminder.id)
            mockReminderRepository.save(completeReminder)
        }

        savedHistory.captured.let{
            assertThat(it.completionStatus).isEqualTo(ReminderStatus.COMPLETED)
            assertThat(it.completedAt.toLocalDateTime()).isEqualTo(
                LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
            )
        }

        verify(exactly = 0) {
            scheduleAlarmForReminder(mockContext, reminder, any())
        }
    }

    @Test
    fun ifNotRepeating_doesNotReschedule() {
        val reminder = Reminder(id = 1, daysOfWeek = sortedSetOf())
        coEvery { mockReminderRepository.findById(reminder.id) } returns reminder
        val completeReminder = reminder.copy(status = ReminderStatus.COMPLETED)
        coEvery { mockReminderRepository.save(completeReminder) } returns reminder.id
        val savedHistory = slot<History>()
        coEvery { mockHistoryRepository.save(capture(savedHistory))} returns 1L

        mockkStatic(::cancelNotification, ::scheduleAlarmForReminder)
        every { cancelNotification(mockContext, reminder.id) } returns Unit

        every { scheduleAlarmForReminder(mockContext, reminder, any()) } returns Alarm(0, 0, 0)
        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_COMPLETE
        every { intent.getLongExtra(EXTRA_REMINDER_ID, 0) } returns reminder.id
        receiver.onReceive(mockContext,intent)

        coVerifyOrder {
            mockReminderRepository.findById(reminder.id)
            mockHistoryRepository.save(any())
            cancelNotification(mockContext, reminder.id)
            mockReminderRepository.save(completeReminder)
        }

        coVerify(exactly = 0) {
            scheduleAlarmForReminder(mockContext, reminder, any())
            mockAlarmRepository.save(any())
        }
    }

    @Test
    fun ifRepeating_doesReschedule_doesNotUpdateReminder() {
        val reminder = Reminder(id = 1, daysOfWeek = sortedSetOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        coEvery { mockReminderRepository.findById(reminder.id) } returns reminder
        val completeReminder = reminder.copy(status = ReminderStatus.COMPLETED)
        coEvery { mockReminderRepository.save(completeReminder) } returns reminder.id
        val savedHistory = slot<History>()
        coEvery { mockHistoryRepository.save(capture(savedHistory))} returns 1L

        mockkStatic(::cancelNotification, ::scheduleAlarmForReminder)
        every { cancelNotification(mockContext, reminder.id) } returns Unit

        val today = LocalDate.now().dayOfWeek
        every { scheduleAlarmForReminder(mockContext, reminder, today) } returns Alarm(0, 0, 0)
        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_COMPLETE
        every { intent.getLongExtra(EXTRA_REMINDER_ID, 0) } returns reminder.id

        coEvery { mockAlarmRepository.save(any()) } returns 0L

        receiver.onReceive(mockContext,intent)

        coVerifyOrder {
            mockReminderRepository.findById(reminder.id)
            mockHistoryRepository.save(any())
            cancelNotification(mockContext, reminder.id)
            scheduleAlarmForReminder(mockContext, reminder, today)
            mockAlarmRepository.save(any())
        }

        coVerify(exactly = 0){
            mockReminderRepository.save(completeReminder)
        }
    }
}