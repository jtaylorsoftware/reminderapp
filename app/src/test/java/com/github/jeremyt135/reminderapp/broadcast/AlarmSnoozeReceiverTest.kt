package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.github.jeremyt135.reminderapp.util.toLocalDateTime
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class AlarmSnoozeReceiverTest {
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    lateinit var receiver: AlarmSnoozeReceiver

    @MockK
    lateinit var mockReminderRepository: ReminderRepository

    @MockK
    lateinit var mockAlarmRepository: AlarmRepository

    @MockK
    lateinit var mockContext: Context

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        receiver = spyk(AlarmSnoozeReceiver())

        receiver.alarmRepository = mockAlarmRepository
        receiver.reminderRepository = mockReminderRepository

        val mockPendingResult = mockk<BroadcastReceiver.PendingResult>()
        every { receiver.goAsync() } returns mockPendingResult
        every { mockPendingResult.finish() } returns Unit
    }

    @Test
    fun intentAction_notAlarmSnooze_returnsWithoutSnooze() = mainCoroutineRule.runBlockingTest {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_BOOT_COMPLETED

        receiver.onReceive(mockContext, intent)

        coVerify(exactly = 0) {
            mockReminderRepository.getReminderAndAlarm(any())
        }
    }

    @Test
    fun intentAction_isAlarmSnooze_snoozesAlarm() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder(id = 1)
        val alarm = Alarm(id = 1, reminderId = 1, triggerAt = LocalDateTime.now().toEpochMilli())
        val reminderAndAlarm = ReminderAndAlarm(reminder, alarm)

        coEvery { mockReminderRepository.getReminderAndAlarm(1) } returns reminderAndAlarm

        val nextAlarm = alarm.copy(
            triggerAt = alarm.triggerAt.toLocalDateTime().plusMinutes(
                SNOOZE_TIME_MIN
            ).toEpochMilli()
        )

        coEvery { mockAlarmRepository.save(nextAlarm) } returns 1

        mockkStatic(::scheduleAlarm, ::cancelAlarm, ::makeSnoozeToast, ::cancelNotification)
        every { scheduleAlarm(mockContext, nextAlarm) } returns Unit
        every { cancelAlarm(mockContext, alarm) } returns Unit
        every { makeSnoozeToast(mockContext) } returns Unit
        every { cancelNotification(mockContext, reminder.id) } returns Unit

        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_SNOOZE
        every { intent.getLongExtra(EXTRA_REMINDER_ID, any()) } returns reminder.id
        receiver.onReceive(mockContext, intent)

        /**
         * Snoozing takes following steps:
         * - get the reminder and its alarm
         * - cancel previous snoozes and alarms
         * - set a new time and save an updated alarm
         * - schedule the alarm with the system
         * - make a toast
         * - cancel the current notification
         */
        coVerifyOrder {
            mockReminderRepository.getReminderAndAlarm(1)
            cancelAlarm(mockContext, alarm)
            mockAlarmRepository.save(nextAlarm)
            scheduleAlarm(mockContext, nextAlarm)
            makeSnoozeToast(mockContext)
            cancelNotification(mockContext, reminder.id)
        }
    }
}