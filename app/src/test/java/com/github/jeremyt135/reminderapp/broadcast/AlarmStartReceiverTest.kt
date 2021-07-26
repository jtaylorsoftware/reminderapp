package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.service.AlarmService
import com.github.jeremyt135.reminderapp.service.AlarmTimerManager
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class AlarmStartReceiverTest {
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    lateinit var receiver: AlarmStartReceiver

    @MockK
    lateinit var mockReminderRepository: ReminderRepository

    @MockK
    lateinit var mockAlarmRepository: AlarmRepository

    @MockK
    lateinit var mockTimerManager: AlarmTimerManager

    @MockK
    lateinit var mockContext: Context

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val mockWorkManager = mockkClass(WorkManager::class)
        every { mockWorkManager.enqueue(any<WorkRequest>()) } returns mockkClass(Operation::class)
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns mockWorkManager

        mockkObject(AlarmService.Companion)
        justRun { AlarmService.start(any(), any(), any()) }

        coJustRun { mockAlarmRepository.deleteAllInPast(any()) }
        coJustRun { mockAlarmRepository.clearAllScheduled() }

        receiver = spyk(AlarmStartReceiver().apply {
            applicationScope = mainCoroutineRule
            reminderRepository = mockReminderRepository
            timerManager = mockTimerManager
        })

        val mockPendingResult = mockk<BroadcastReceiver.PendingResult>()
        every { mockTimerManager.startTimer(any(), any(), any()) } returns true
        every { receiver.goAsync() } returns mockPendingResult
        every { mockPendingResult.finish() } returns Unit
    }

    @Test
    fun intentAction_notAlarmStart_returns() {
        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_SNOOZE

        receiver.onReceive(mockContext, intent)

        verify(exactly = 0) {
            intent.getStringExtra(EXTRA_REMINDER_ID)
        }
    }

    @Test
    fun getsNullReminder_schedulesNext() {
        val reminder = Reminder()

        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id
        coEvery { mockReminderRepository.getReminderAndAlarm(reminder.id) } returns null
        receiver.onReceive(mockContext, intent)

        verify {
            AlarmActionHandler.createForAction(ACTION_ALARM_SCHEDULE_NEXT, "")
        }
    }

    @Test
    fun getsReminder_startsService() {
        val reminder = Reminder()
        val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = currentLocalDateTime())

        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id
        coEvery { mockReminderRepository.getReminderAndAlarm(reminder.id) } returns
                ReminderAndAlarm(
                    reminder,
                    alarm
                )

        receiver.onReceive(mockContext, intent)

        verify {
            AlarmActionHandler.createForAction(ACTION_ALARM_SCHEDULE_NEXT, "")
            AlarmService.start(mockContext, reminder, alarm)
        }
    }
}