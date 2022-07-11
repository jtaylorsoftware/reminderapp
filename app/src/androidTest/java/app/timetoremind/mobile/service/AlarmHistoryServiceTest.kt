package app.timetoremind.mobile.service

import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.MediumTest
import androidx.test.rule.ServiceTestRule
import app.timetoremind.mobile.broadcast.ACTION_ALARM_COMPLETE
import app.timetoremind.mobile.broadcast.ACTION_ALARM_SKIP
import app.timetoremind.mobile.broadcast.EXTRA_REMINDER
import app.timetoremind.mobile.data.HistoryRepository
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.data.domain.History
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.data.domain.ReminderStatus
import app.timetoremind.mobile.data.domain.parcel.ReminderParcel
import app.timetoremind.sharedtest.MainCoroutineRule
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import javax.inject.Inject
import kotlin.properties.Delegates

@ExperimentalCoroutinesApi
@HiltAndroidTest
@MediumTest
class AlarmHistoryServiceTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val serviceTestRule = ServiceTestRule()

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var historyRepository: HistoryRepository

    val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var binder: IBinder
    private var startCommand by Delegates.notNull<Int>()

    @Before
    fun setup() {
        hiltRule.inject()
        startCommand = 0
    }

    private fun createService(): AlarmHistoryService {
        val intent = Intent(context, AlarmHistoryService::class.java)
        binder = serviceTestRule.bindService(intent)
        return (binder as ReminderService.Binder).getService()!!
    }

    private fun AlarmHistoryService.doOnStartCommand(reminder: Reminder, status: ReminderStatus) {
        val statusAction = when (status) {
            ReminderStatus.SKIPPED -> ACTION_ALARM_SKIP
            ReminderStatus.COMPLETED -> ACTION_ALARM_COMPLETE
            else -> throw IllegalArgumentException("Status must be SKIPPED or COMPLETED")
        }
        val intent = Intent(context, AlarmHistoryService::class.java).apply {
            action = statusAction
            putExtra(EXTRA_REMINDER, ReminderParcel(reminder))
        }
        onStartCommand(intent, 0, startCommand++)
    }

    @Test
    fun actionSkip_createsSkipHistory() = runTest {
        val reminder = Reminder()
        reminderRepository.save(reminder)

        val service = createService()
        service.doOnStartCommand(reminder, ReminderStatus.SKIPPED)

        val history = historyRepository.findByReminderId(reminder.id).first()
        assertThat(history).hasSize(1)
        assertThat(history[0].copy(id = "", completedAt = 0)).isEqualTo(
            History(
                id = "",
                reminderId = reminder.id,
                description = reminder.description,
                completionStatus = ReminderStatus.SKIPPED
            )
        )
        assertThat(history[0].completedAt).isAtMost(Instant.now().toEpochMilli())
    }

    @Test
    fun actionComplete_createsCompleteHistory() = runTest {
        val reminder = Reminder()
        reminderRepository.save(reminder)

        val service = createService()
        service.doOnStartCommand(reminder, ReminderStatus.COMPLETED)

        val history = historyRepository.findByReminderId(reminder.id).first()
        assertThat(history).hasSize(1)
        assertThat(history[0].copy(id = "", completedAt = 0)).isEqualTo(
            History(
                id = "",
                reminderId = reminder.id,
                description = reminder.description,
                completionStatus = ReminderStatus.COMPLETED
            )
        )
        assertThat(history[0].completedAt).isAtMost(Instant.now().toEpochMilli())
    }

    @Test
    fun handlesMultipleStarts() = runTest {
        val reminders = listOf(Reminder(), Reminder(), Reminder())
        reminderRepository.saveAll(reminders)

        val service = createService()
        val statuses = mutableListOf<ReminderStatus>()
        reminders.forEachIndexed { i, reminder ->
            statuses.add(if (i % 2 == 0) ReminderStatus.COMPLETED else ReminderStatus.SKIPPED)
            service.doOnStartCommand(
                reminder,
                statuses.last()
            )
        }

        assertThat(service.totalStartCommands).isEqualTo(reminders.size)
        assertThat(service.startCommands).isEmpty()

        val historyEntries = historyRepository.getAll().first()
        assertThat(historyEntries).hasSize(reminders.size)
        reminders.forEachIndexed { i, reminder ->
            val history = historyEntries[i]
            assertThat(history.copy(id = "", completedAt = 0)).isEqualTo(
                History(
                    id = "",
                    reminderId = reminder.id,
                    description = reminder.description,
                    completionStatus = statuses[i]
                )
            )
            assertThat(history.completedAt).isAtMost(Instant.now().toEpochMilli())
        }
    }
}