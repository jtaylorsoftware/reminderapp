package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.asDomainModel
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.local.ReminderEntity
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@ExperimentalCoroutinesApi
class AlarmBootSchedulerTest {
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    lateinit var receiver: AlarmBootScheduler

    @MockK
    lateinit var mockReminderRepository: ReminderRepository

    @MockK
    lateinit var mockAlarmRepository: AlarmRepository

    @MockK
    lateinit var mockContext: Context

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        receiver = spyk(AlarmBootScheduler().apply {
            mainDispatcher = Dispatchers.Main
            alarmRepository = mockAlarmRepository
            reminderRepository = mockReminderRepository
        })

        val mockPendingResult = mockk<BroadcastReceiver.PendingResult>()
        every { receiver.goAsync() } returns mockPendingResult
        every { mockPendingResult.finish() } returns Unit
    }

    @Test
    fun intentAction_notBootCompleted_returnsWithoutScheduling() {
        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_SNOOZE

        receiver.onReceive(mockContext, intent)

        // Verify that no other methods will be called
        coVerify(exactly = 0) {
            mockAlarmRepository.deleteAll()
        }
    }

    @Test
    fun intentAction_isBootCompleted_schedulesAlarms() = mainCoroutineRule.runBlockingTest {
        val reminders = listOf(
            ReminderEntity(id = 1),
            ReminderEntity(id = 2)
        ).asDomainModel()
        val alarms = reminders.mapIndexed { i, reminder ->
            Alarm(i + 1L, reminder.id, 0)
        }

        val today = LocalDate.now().dayOfWeek

        mockkStatic(::scheduleNextAlarm)
        coEvery { scheduleNextAlarm(mockContext, mockAlarmRepository, mockReminderRepository) } returns Unit

        coEvery { mockAlarmRepository.save(alarm = any()) } returnsMany listOf(1, 2)
        coEvery { mockReminderRepository.getAll() } returns flowOf(reminders)
        coEvery { mockAlarmRepository.deleteAll() } returns Unit

        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_BOOT_COMPLETED
        every { intent.getLongExtra(EXTRA_REMINDER_ID, any()) } returnsMany listOf(1, 2)

        receiver.onReceive(mockContext, intent)

        coVerifyOrder {
            scheduleNextAlarm(mockContext, mockAlarmRepository, mockReminderRepository)
        }
    }
}