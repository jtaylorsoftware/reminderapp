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
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class AlarmSchedulerTest {
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    lateinit var receiver: AlarmScheduler

    @MockK
    lateinit var mockReminderRepository: ReminderRepository

    @MockK
    lateinit var mockAlarmRepository: AlarmRepository

    @MockK
    lateinit var mockContext: Context

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockkStatic(::rescheduleReminders)
        coJustRun { rescheduleReminders(any(), any(), any()) }

        receiver = spyk(AlarmScheduler().apply {
            applicationScope = mainCoroutineRule
            alarmRepository = mockAlarmRepository
            reminderRepository = mockReminderRepository
        })

        val mockPendingResult = mockk<BroadcastReceiver.PendingResult>()
        every { receiver.goAsync() } returns mockPendingResult
        every { mockPendingResult.finish() } returns Unit
    }

    @Test
    fun alarmScheduler_intentAction_notExpected_returnsWithoutScheduling() {
        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_SNOOZE

        receiver.onReceive(mockContext, intent)

        // Verify that no other methods will be called
        coVerify(exactly = 0) {
            mockAlarmRepository.deleteAll()
        }
    }

    @Test
    fun alarmScheduler_intentAction_isBootCompleted_schedulesAlarms() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_BOOT_COMPLETED

        receiver.onReceive(mockContext, intent)

        coVerifyOrder {
            rescheduleReminders(any(), any(), any())
        }
    }

    @Test
    fun alarmScheduler_intentAction_isMyPackageReplaced_schedulesAlarms() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_MY_PACKAGE_REPLACED

        receiver.onReceive(mockContext, intent)

        coVerifyOrder {
            rescheduleReminders(any(), any(), any())
        }
    }
}