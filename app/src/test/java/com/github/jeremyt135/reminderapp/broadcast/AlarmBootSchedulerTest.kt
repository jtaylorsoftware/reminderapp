package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.local.Alarm
import com.github.jeremyt135.reminderapp.data.local.ReminderEntity
import com.github.jeremyt135.reminderapp.data.local.asDomainModel
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

@ExperimentalCoroutinesApi
class AlarmBootSchedulerTest {
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @SpyK
    var receiver: AlarmBootScheduler = AlarmBootScheduler()

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
    }

    @Test
    fun intentAction_notBootCompleted_returnsWithoutScheduling() {
        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START

        coEvery { mockAlarmRepository.deleteAll() } returns Unit

        receiver.onReceive(mockContext, intent)

        // Verify that no other methods will be called
        coVerify(exactly = 0) {
            mockAlarmRepository.deleteAll()
        }
    }

    @Test
    fun intentAction_isBootCompleted_schedulesAlarms() = mainCoroutineRule.runBlockingTest {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_BOOT_COMPLETED

        val reminders = listOf(
            ReminderEntity(id = 1),
            ReminderEntity(id = 2)
        ).asDomainModel()
        val alarms = reminders.mapIndexed { i, reminder ->
            Alarm(i + 1L, reminder.id, 0)
        }

        val today = LocalDate.now().dayOfWeek

        mockkStatic(::scheduleAlarmForReminder)
        every { scheduleAlarmForReminder(mockContext, reminder = any(), today) } returnsMany alarms

        coEvery { mockAlarmRepository.deleteAll() } returns Unit
        coEvery { mockAlarmRepository.save(alarm = any()) } returnsMany listOf(1,2)
        coEvery { mockReminderRepository.getAll() } returns flowOf(reminders)

        receiver.onReceive(mockContext, intent)

        coVerifyOrder {
            // Verify previous were deleted
            mockAlarmRepository.deleteAll()

            // Verify that 2 reminders were scheduled
            scheduleAlarmForReminder(mockContext, reminders[0], today)
            mockAlarmRepository.save(alarms[0])
            scheduleAlarmForReminder(mockContext, reminders[1], today)
            mockAlarmRepository.save(alarms[1])
        }
    }
}