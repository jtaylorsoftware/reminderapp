package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver.PendingResult
import android.content.Context
import android.content.Intent
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
    lateinit var mockContext: Context

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        receiver.reminderRepository = mockReminderRepository
        receiver.alarmRepository = mockAlarmRepository

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

        mockkStatic(::cancelNotification)
        every { cancelNotification(mockContext, reminder.id) } returns Unit

        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_COMPLETE
        every { intent.getLongExtra(EXTRA_REMINDER_ID, 0) } returns reminder.id
        receiver.onReceive(mockContext,intent)

        coVerifyOrder {
            mockReminderRepository.findById(reminder.id)
            mockReminderRepository.save(completeReminder)
            cancelNotification(mockContext, reminder.id)
        }
    }
}