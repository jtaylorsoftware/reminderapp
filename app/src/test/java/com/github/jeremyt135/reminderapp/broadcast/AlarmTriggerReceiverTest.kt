package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Vibrator
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.data.local.Alarm
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class AlarmTriggerReceiverTest {
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    lateinit var receiver: AlarmTriggerReceiver

    @MockK
    lateinit var mockReminderRepository: ReminderRepository

    @MockK
    lateinit var mockAlarmRepository: AlarmRepository

    @MockK
    lateinit var mockContext: Context

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        receiver = spyk(AlarmTriggerReceiver())
        receiver.alarmRepository = mockAlarmRepository
        receiver.reminderRepository = mockReminderRepository

        val mockPendingResult = mockk<BroadcastReceiver.PendingResult>()
        every { receiver.goAsync() } returns mockPendingResult
        every { mockPendingResult.finish() } returns Unit
    }

    @Test
    fun intentAction_notAlarmStart_returnsWithoutTrigger() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_BOOT_COMPLETED

        receiver.onReceive(mockContext, intent)

        // Verify that reminder isn't searched, so nothing else called either
        coVerify(exactly = 0) {
            mockReminderRepository.findById(0)
        }
    }

    @Test
    fun intentAction_isAlarmStart_checksAlarmShouldTrigger() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder(id = 1)
        val alarm = Alarm(1, reminder.id, 0)
        val reminderAndAlarm = ReminderAndAlarm(reminder, alarm)

        coEvery { mockReminderRepository.getReminderAndAlarm(alarm.reminderId) } returns reminderAndAlarm

        mockkStatic(::alarmShouldTriggerNow)

        every { alarmShouldTriggerNow(reminder, alarm, any()) } returns false

        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START
        every { intent.getLongExtra(EXTRA_REMINDER_ID, any()) } returns 1L

        receiver.onReceive(mockContext, intent)

        coVerifyAll {
            mockReminderRepository.getReminderAndAlarm(alarm.reminderId)
            alarmShouldTriggerNow(reminder, alarm, any())
        }
    }

    @Test
    fun whenAlarmShouldTriggerNow_triggersNotification() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder(id = 1)
        val alarm = Alarm(1, reminder.id, 0)
        val reminderAndAlarm = ReminderAndAlarm(reminder, alarm)

        coEvery { mockReminderRepository.getReminderAndAlarm(alarm.reminderId) } returns reminderAndAlarm

        mockkStatic(::alarmShouldTriggerNow, ::triggerNotification)

        every { alarmShouldTriggerNow(reminder, alarm, any()) } returns true
        every { triggerNotification(mockContext, reminder) } returns Unit

        val vibrator = mockk<Vibrator>()
        every { mockContext.getSystemService(Context.VIBRATOR_SERVICE) } returns vibrator
        every { vibrator.hasVibrator() } returns false

        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START
        every { intent.getLongExtra(EXTRA_REMINDER_ID, any()) } returns 1L

        receiver.onReceive(mockContext, intent)

        coVerifyAll {
            mockReminderRepository.getReminderAndAlarm(alarm.reminderId)
            alarmShouldTriggerNow(reminder, alarm, any())
            triggerNotification(mockContext, reminder)
        }
    }
}