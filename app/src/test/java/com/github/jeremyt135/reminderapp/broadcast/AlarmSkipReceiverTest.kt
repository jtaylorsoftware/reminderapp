package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver.PendingResult
import android.content.Context
import android.content.Intent
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.github.jeremyt135.reminderapp.util.toLocalDateTime
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@ExperimentalCoroutinesApi
class AlarmSkipReceiverTest {
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @SpyK
    var receiver: AlarmSkipReceiver = AlarmSkipReceiver()

    @MockK
    lateinit var mockReminderRepository: ReminderRepository

    @MockK
    lateinit var mockAlarmRepository: AlarmRepository

    @MockK
    lateinit var mockHistoryRepository: HistoryRepository

    @MockK
    lateinit var mockContext: Context

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        receiver.reminderRepository = mockReminderRepository
        receiver.alarmRepository = mockAlarmRepository
        receiver.historyRepository = mockHistoryRepository

        val mockPendingResult = mockk<PendingResult>()
        every { receiver.goAsync() } returns mockPendingResult
        every { mockPendingResult.finish() } returns Unit
    }

    @Test
    fun intentAction_notAlarmSkip_returnsWithoutSkip() {
        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_COMPLETE

        receiver.onReceive(mockContext, intent)

        coVerify(exactly = 0) {
            mockReminderRepository.findById(any())
        }
    }

    @Test
    fun intentAction_isAlarmSkip_marksReminderSkipped() {
        val reminder = Reminder(id = 1)
        coEvery { mockReminderRepository.findById(reminder.id) } returns reminder
        val skippedReminder = reminder.copy(status = ReminderStatus.SKIPPED)
        coEvery { mockReminderRepository.save(skippedReminder) } returns reminder.id
        val savedHistory = slot<History>()
        coEvery { mockHistoryRepository.save(capture(savedHistory)) } returns 1L

        mockkStatic(::cancelNotification)
        every { cancelNotification(mockContext, reminder.id) } returns Unit

        val intent = mockk<Intent>()
        every { intent.action } returns ACTION_ALARM_SKIP
        every { intent.getLongExtra(EXTRA_REMINDER_ID, 0) } returns reminder.id
        receiver.onReceive(mockContext, intent)

        coVerifyOrder {
            mockReminderRepository.findById(reminder.id)
            mockReminderRepository.save(skippedReminder)
            mockHistoryRepository.save(any())
            cancelNotification(mockContext, reminder.id)
        }

        savedHistory.captured.let {
            assertThat(it.completionStatus).isEqualTo(ReminderStatus.SKIPPED)
            assertThat(it.completedAt.toLocalDateTime()).isEqualTo(
                LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
            )
        }
    }
}