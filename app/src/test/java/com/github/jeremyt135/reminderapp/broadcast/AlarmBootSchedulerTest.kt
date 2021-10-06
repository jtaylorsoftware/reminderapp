package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.service.AlarmSchedulingService
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AlarmBootSchedulerTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    lateinit var receiver: AlarmBootScheduler

    @MockK
    lateinit var mockReminderRepository: ReminderRepository

    @MockK
    lateinit var mockAlarmRepository: AlarmRepository

    val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        hiltRule.inject()
        MockKAnnotations.init(this)

        mockkObject(AlarmSchedulingService.Companion)
        justRun { AlarmSchedulingService.startRescheduleAll(any()) }

        receiver = spyk(AlarmBootScheduler().apply {
            applicationScope = mainCoroutineRule
            alarmRepository = mockAlarmRepository
            reminderRepository = mockReminderRepository
        })

        val mockPendingResult = mockk<BroadcastReceiver.PendingResult>()
        every { receiver.goAsync() } returns mockPendingResult
        every { mockPendingResult.finish() } returns Unit
    }

    @Test
    fun intentAction_notExpected_returnsWithoutScheduling() {
        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_SNOOZE

        receiver.onReceive(context, intent)

        verify(exactly = 0) {
            AlarmSchedulingService.startRescheduleAll(any())
        }
    }

    @Test
    fun intentAction_isBootCompleted_schedulesAlarms() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_BOOT_COMPLETED

        receiver.onReceive(context, intent)

        verify {
            AlarmSchedulingService.startRescheduleAll(any())
        }
    }

    @Test
    fun intentAction_isMyPackageReplaced_schedulesAlarms() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_MY_PACKAGE_REPLACED

        receiver.onReceive(context, intent)

        verify {
            AlarmSchedulingService.startRescheduleAll(any())
        }
    }

    @Test
    fun intentAction_TimezoneChanged_schedulesAlarms() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_TIMEZONE_CHANGED

        receiver.onReceive(context, intent)

        verify {
            AlarmSchedulingService.startRescheduleAll(any())
        }
    }
}