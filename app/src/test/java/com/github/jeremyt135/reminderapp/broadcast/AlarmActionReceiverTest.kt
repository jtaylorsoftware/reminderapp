package com.github.jeremyt135.reminderapp.broadcast

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.service.AlarmMediaService
import com.github.jeremyt135.reminderapp.service.AlarmTimerManager
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AlarmActionReceiverTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Inject
    lateinit var timerManager: AlarmTimerManager

    lateinit var receiver: AlarmActionReceiver

    val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        hiltRule.inject()

        val mockWorkManager = mockkClass(WorkManager::class)
        every { mockWorkManager.enqueue(any<WorkRequest>()) } returns mockkClass(Operation::class)
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns mockWorkManager

        mockkObject(AlarmMediaService.Companion)
        justRun { AlarmMediaService.requestStop(any()) }
        justRun { AlarmMediaService.start(any(), any()) }

        mockkObject(AlarmActionHandler.Companion)
        every { AlarmActionHandler.createForAction(any(), any()) } returns
                OneTimeWorkRequestBuilder<AlarmActionHandler>().build()

        receiver = AlarmActionReceiver()
    }

    @Test
    fun intentAction_null_returns() = mainCoroutineRule.runBlockingTest {
        val intent = mockk<Intent>()
        every { intent.action } returns null

        receiver.onReceive(context, intent)

        verify(exactly = 0) {
            intent.getStringExtra(EXTRA_REMINDER_ID)
        }
    }

    @Test
    fun intentAction_notExpected_returns() = mainCoroutineRule.runBlockingTest {
        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_START

        receiver.onReceive(context, intent)

        verify(exactly = 0) {
            intent.getStringExtra(EXTRA_REMINDER_ID)
        }
    }

    @Test
    fun stopsAlarmService_andCreatesWorker() = mainCoroutineRule.runBlockingTest {
        val reminder = Reminder()

        val action = ACTION_ALARM_SNOOZE
        val intent = mockk<Intent>()

        every { intent.action } returns action
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns reminder.id

        receiver.onReceive(context, intent)

        verify {
            AlarmMediaService.requestStop(context)
            AlarmActionHandler.createForAction(action, reminder.id)
        }
    }

    @Test
    fun stopsAlarmManagerTimerInstance() = mainCoroutineRule.runBlockingTest {
        val action = ACTION_ALARM_SNOOZE
        val intent = mockk<Intent>()

        every { intent.action } returns action
        every { intent.getStringExtra(EXTRA_REMINDER_ID) } returns ""

        pauseDispatcher()
        timerManager.startTimer(mainCoroutineRule) {}
        receiver.onReceive(context, intent)

        verify {
            AlarmMediaService.requestStop(context)
            AlarmActionHandler.createForAction(action, "")
        }

        assertThat(timerManager.timer!!.isFinished).isTrue()
    }
}