package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Vibrator
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.local.Alarm
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class AlarmTriggerReceiverTest {
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @SpyK
    var receiver: AlarmTriggerReceiver = AlarmTriggerReceiver()

    @MockK
    lateinit var mockReminderRepository: ReminderRepository

    @MockK
    lateinit var mockAlarmRepository: AlarmRepository

    @MockK
    lateinit var mockContext: Context

    companion object {
        const val ACTION_ALARM_START = "ALARM_START"
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        receiver.alarmRepository = mockAlarmRepository
        receiver.reminderRepository = mockReminderRepository

        val mockPendingResult = mockk<BroadcastReceiver.PendingResult>()
        every { receiver.goAsync() } returns mockPendingResult
        every { mockPendingResult.finish() } returns Unit

        every { mockContext.getString(R.string.action_alarm_start) } returns ACTION_ALARM_START
    }

    @Test
    fun intentAction_notAlarmStart_returnsWithoutTrigger() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_BOOT_COMPLETED
        every { intent.getParcelableExtra<Alarm>("alarm") } returns Alarm(0, 0, 0)

        coEvery { mockReminderRepository.findById(0) } returns null

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

        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START
        every { intent.getParcelableExtra<Alarm>("alarm") } returns alarm

        coEvery { mockReminderRepository.findById(alarm.reminderId) } returns reminder

        mockkStatic(::alarmShouldTriggerNow)
        every { alarmShouldTriggerNow(reminder, alarm) } returns false

        receiver.onReceive(mockContext, intent)

        coVerifyAll {
            mockReminderRepository.findById(alarm.reminderId)
            alarmShouldTriggerNow(reminder, alarm)
        }
    }

    @Test
    fun whenAlarmShouldTriggerNow_triggersNotification() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder(id = 1)
        val alarm = Alarm(1, reminder.id, 0)

        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START
        every { intent.getParcelableExtra<Alarm>("alarm") } returns alarm

        coEvery { mockReminderRepository.findById(alarm.reminderId) } returns reminder

        mockkStatic(::alarmShouldTriggerNow)
        every { alarmShouldTriggerNow(reminder, alarm) } returns true
        every { triggerNotification(mockContext, alarm, reminder) } returns Unit

        val vibrator = mockk<Vibrator>()
        every { mockContext.getSystemService(Context.VIBRATOR_SERVICE) } returns vibrator
        every { vibrator.hasVibrator() } returns false

        receiver.onReceive(mockContext, intent)

        coVerifyAll {
            mockReminderRepository.findById(alarm.reminderId)
            alarmShouldTriggerNow(reminder, alarm)
            triggerNotification(mockContext, alarm, reminder)
        }
    }
}