package app.timetoremind.mobile.data

import app.timetoremind.mobile.data.domain.Alarm
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.sharedtest.MainCoroutineRule
import app.timetoremind.sharedtest.data.local.FakeReminderLocalDataSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class ReminderRepositoryTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var repository: ReminderRepository
    private lateinit var fakeLocalDataSource: ReminderDataSource

    private val localReminders = listOf(
        Reminder("Reminder 1"),
        Reminder("Reminder 2")
    )

    private val alarms = localReminders.mapIndexed { i, reminder ->
        Alarm(id = i.toLong() + 1, reminderId = reminder.id)
    }

    @Before
    fun setup() {
        fakeLocalDataSource = FakeReminderLocalDataSource(localReminders.toMutableList(), alarms.toMutableList())
        repository = ReminderRepositoryImpl(
            fakeLocalDataSource
        )
    }

    @Test
    fun getAll_returnsRemindersFromLocalDataSource() = runTest {
        val repoReminders = repository.getAll().first()

        assertThat(repoReminders).containsExactlyElementsIn(localReminders)
    }

    @Test
    fun findById_returnsReminderInLocalDataSource() = runTest {
        val reminder = localReminders[1]
        val foundReminder = repository.findById(reminder.id)

        assertThat(foundReminder).isEqualTo(reminder)
    }

    @Test
    fun save_addsReminderToLocalDataSource() = runTest {
        val reminder = Reminder("Reminder 3")

        repository.save(reminder)

        val savedReminder = fakeLocalDataSource.findById(reminder.id)

        assertThat(savedReminder).isNotNull()
    }

    @Test
    fun getReminderAndAlarm_returnsReminderWithAlarm() = runTest {
        val (reminder, alarm) = repository.getReminderAndAlarm(localReminders[0].id)!!
        assertThat(reminder).isEqualTo(localReminders[0])
        assertThat(alarm).isEqualTo(alarms[0])
    }

    @Test
    fun delete_removesReminder() = runTest {
        val reminder = localReminders[0]

        repository.delete(reminder)

        assertThat(repository.findById(reminder.id)).isNull()
    }
}