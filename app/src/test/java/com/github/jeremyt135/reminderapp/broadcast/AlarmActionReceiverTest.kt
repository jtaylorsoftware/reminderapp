package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.service.AlarmService
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class AlarmActionReceiverTest {
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    lateinit var receiver: AlarmActionReceiver

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
        justRun { AlarmService.requestStop(any()) }
        justRun { AlarmService.start(any(), any(), any()) }

        mockkObject(AlarmActionHandler.Companion)
        every { AlarmActionHandler.createForAction(any(), any()) } returns
                OneTimeWorkRequestBuilder<AlarmActionHandler>().build()

        receiver = spyk(AlarmActionReceiver())

        val mockPendingResult = mockk<BroadcastReceiver.PendingResult>()
        every { receiver.goAsync() } returns mockPendingResult
        every { mockPendingResult.finish() } returns Unit
    }

    @Test
    fun intentAction_null_returns() {
        val intent = mockk<Intent>()
        every { intent.action } returns null

        receiver.onReceive(mockContext, intent)

        verify(exactly = 0) {
            intent.getStringExtra(EXTRA_REMINDER_ID)
        }
    }

    @Test
    fun intentAction_notExpected_returns() {
        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START

        receiver.onReceive(mockContext, intent)

        verify(exactly = 0) {
            intent.getStringExtra(EXTRA_REMINDER_ID)
        }
    }

    @Test
    fun stopsAlarmService_andCreatesWorker() {
        val reminder = Reminder()

        val action = ACTION_ALARM_SNOOZE
        val intent = mockk<Intent>()

        every { intent.action } returns action
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id

        receiver.onReceive(mockContext, intent)

        verify {
            AlarmService.requestStop(mockContext)
            AlarmActionHandler.createForAction(action, reminder.id)
        }
    }
}