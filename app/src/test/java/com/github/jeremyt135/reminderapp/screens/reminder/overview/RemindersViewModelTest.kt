package com.github.jeremyt135.reminderapp.screens.reminder.overview

import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.work.WorkManager
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.broadcast.cancelAlarmIntentsForReminder
import com.github.jeremyt135.reminderapp.data.*
import com.github.jeremyt135.reminderapp.data.domain.*
import com.github.jeremyt135.reminderapp.data.local.FakeHistoryLocalDataSource
import com.github.jeremyt135.reminderapp.getOrAwaitValue
import com.github.jeremyt135.reminderapp.service.AlarmService
import com.github.jeremyt135.reminderapp.util.currentEpochMilli
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

@ExperimentalCoroutinesApi
class RemindersViewModelTest {
    private lateinit var fakeReminderRepository: ReminderRepository
    private lateinit var fakeAlarmRepository: AlarmRepository
    private lateinit var fakeHistoryRepository: FakeHistoryRepository
    private lateinit var viewModel: RemindersViewModel
    private lateinit var reminders: List<Reminder>
    private lateinit var alarms: MutableList<Alarm>
    private lateinit var historyEntries: MutableList<History>

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        reminders = listOf(
            Reminder("1", time = LocalTime.now().plusMinutes(1)),
            Reminder("2", time = LocalTime.now().plusMinutes(2)),
            Reminder("3", time = LocalTime.now().plusMinutes(3)),
            Reminder(
                "4",
                daysOfWeek = sortedSetOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
                time = LocalTime.now().plusMinutes(4)
            ),
            Reminder(
                "5",
                daysOfWeek = DayOfWeek.values().toSortedSet(),
                time = LocalTime.now().plusMinutes(5)
            ),
            Reminder(
                "6",
                daysOfWeek = sortedSetOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                time = LocalTime.now().plusMinutes(6)
            ),
            Reminder(
                "7",
                daysOfWeek = sortedSetOf(DayOfWeek.MONDAY),
                time = LocalTime.now().plusMinutes(7)
            ),
        )
        alarms = mutableListOf()
        historyEntries = mutableListOf()

        fakeReminderRepository =
            FakeReminderRepository(
                reminders.toMutableList(),
                alarms,
                historyEntries
            )
        fakeAlarmRepository = FakeAlarmRepository(alarms)
        fakeHistoryRepository = FakeHistoryRepository(FakeHistoryLocalDataSource(historyEntries))
        val mockSharedPrefs = mockkClass(SharedPreferences::class, relaxed = true)

        every { mockSharedPrefs.edit() } returns mockk(relaxed = true)
        every {
            mockSharedPrefs.getString("FILTER_REMINDER_STATUS", any())
        } returns ReminderStatus.values().joinToString(",")
        every {
            mockSharedPrefs.getString("FILTER_REMINDER_DAYS", any())
        } returns DayOfWeek.values().joinToString(",")
        every {
            mockSharedPrefs.getBoolean("FILTER_REMINDER_TODAY", any())
        } returns true
        every { mockSharedPrefs.getString("SORT_REMINDER_TIME", "NONE") } returns "NONE"
        every { mockSharedPrefs.getString("SORT_REMINDER_DESCRIPTION", "NONE") } returns "NONE"

        mockkObject(AlarmService.Companion)
        justRun { AlarmService.requestStop(any()) }
        mockkObject(AlarmActionHandler.Companion)
        every { AlarmActionHandler.createForAction(any(), any()) } returns mockk(relaxed = true)
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns mockk(relaxed = true)
        mockkStatic(::cancelAlarmIntentsForReminder)
        justRun { cancelAlarmIntentsForReminder(any(), any()) }

        viewModel = RemindersViewModel(
            mockk(relaxed = true),
            fakeReminderRepository,
            fakeAlarmRepository,
            mockSharedPrefs,
            mainCoroutineRule.dispatcher
        )
    }

    @Test
    fun reminders_returnsRepoRemindersAsLiveData() = runBlockingTest(mainCoroutineRule.dispatcher)
    {
        val remindersWithNoHistory = reminders.map {
            ReminderAndHistory(
                reminder = it.copy(),
                history = null
            )
        }
        assertThat(viewModel.reminders.getOrAwaitValue()).containsExactlyElementsIn(
            remindersWithNoHistory
        )
    }

    @Test
    fun addReminder_emitsNavigateToEditor() = runBlockingTest(mainCoroutineRule.dispatcher) {
        viewModel.addReminder()
        assertThat(viewModel.navigateToEditor.peek()).isNotNull()
    }

    @Test
    fun editReminder_emitsNavigateToEditorWithSameReminder() =
        runBlockingTest(mainCoroutineRule.dispatcher) {
            val reminder = reminders[0]
            viewModel.editReminder(reminder)
            assertThat(viewModel.navigateToEditor.peek()).isNotNull()
        }

    @Test
    fun `deleteReminder deletes reminder and reschedules`() = mainCoroutineRule.runBlockingTest {
        val reminder = reminders[0]
        val id = reminder.id
        val alarm = Alarm(id = 1, reminderId = id, scheduled = true)
        fakeAlarmRepository.save(alarm)

        viewModel.deleteReminder(reminder)

        assertThat(fakeReminderRepository.findById(id)).isNull()
        assertThat(fakeAlarmRepository.findById(1)).isNull()
        verify {
            AlarmService.requestStop(any())
            AlarmActionHandler.createForAction(any(), any())
            cancelAlarmIntentsForReminder(any(), reminder)
        }
    }

    @Test
    fun `deleteAllReminders removes all reminders and their alarms`() =
        mainCoroutineRule.runBlockingTest {
            val alarm = Alarm(id = 1, reminderId = reminders[0].id, scheduled = true)
            fakeAlarmRepository.save(alarm)

            viewModel.deleteAllReminders()

            fakeReminderRepository.getAll().collect {
                assertThat(it).isEmpty()
            }
            fakeAlarmRepository.getAll().collect {
                assertThat(it).isEmpty()
            }
        }

    @Test
    fun `reminders list matches filter rules for completion status`() =
        mainCoroutineRule.runBlockingTest {
            val completedEntry = History(
                reminderId = reminders[0].id,
                completionStatus = ReminderStatus.COMPLETED,
                completedAt = currentEpochMilli()
            )
            val skippedEntry = History(
                reminderId = reminders[1].id,
                completionStatus = ReminderStatus.SKIPPED,
                completedAt = currentEpochMilli()
            )
            historyEntries.addAll(listOf(skippedEntry, completedEntry))

            viewModel.changeFilterRules(ReminderFilterRules(reminderStatuses = setOf(ReminderStatus.PENDING)))

            assertThat(viewModel.reminders.getOrAwaitValue()).containsExactlyElementsIn(
                reminders.slice(2 until reminders.size).map { ReminderAndHistory(it, null) }
            )

            viewModel.changeFilterRules(ReminderFilterRules(reminderStatuses = setOf(ReminderStatus.COMPLETED)))
            assertThat(viewModel.reminders.getOrAwaitValue()).containsExactlyElementsIn(
                listOf(
                    ReminderAndHistory(reminders[0], completedEntry)
                )
            )

            viewModel.changeFilterRules(ReminderFilterRules(reminderStatuses = setOf(ReminderStatus.SKIPPED)))
            assertThat(viewModel.reminders.getOrAwaitValue()).containsExactlyElementsIn(
                listOf(
                    ReminderAndHistory(reminders[1], skippedEntry)
                )
            )

        }

    @Test
    fun `reminders list matches filter rules for day of week`() =
        mainCoroutineRule.runBlockingTest {
            val remindersWithHistory = mutableListOf<List<ReminderAndHistory>>().let {
                fakeReminderRepository.getAllWithTodayHistory().toCollection(it)
            }.first()

            // Should contain all reminders
            viewModel.changeFilterRules(
                ReminderFilterRules(
                    daysOfWeek = DayOfWeek.values().toSet()
                )
            )
            assertThat(viewModel.reminders.getOrAwaitValue()).containsExactlyElementsIn(
                remindersWithHistory
            )

            // Should only contain MONDAY
            viewModel.changeFilterRules(
                ReminderFilterRules(
                    includeToday = false,
                    daysOfWeek = setOf(DayOfWeek.MONDAY)
                )
            )
            assertThat(viewModel.reminders.getOrAwaitValue()).containsExactlyElementsIn(
                remindersWithHistory.filter { it.reminder.dayIsChecked(DayOfWeek.MONDAY) })

            // Should only contain with FRIDAY
            viewModel.changeFilterRules(
                ReminderFilterRules(
                    includeToday = false,
                    daysOfWeek = setOf(DayOfWeek.FRIDAY)
                )
            )
            assertThat(viewModel.reminders.getOrAwaitValue()).containsExactlyElementsIn(
                remindersWithHistory.filter { it.reminder.dayIsChecked(DayOfWeek.FRIDAY) })

            // Should only contain the one-offs
            viewModel.changeFilterRules(
                ReminderFilterRules(
                    includeToday = true,
                    daysOfWeek = emptySet(),
                )
            )
            assertThat(viewModel.reminders.getOrAwaitValue()).containsExactlyElementsIn(
                remindersWithHistory.filter { it.reminder.isOneOff })
        }

    @Test
    fun `reminders list order matches sorting rules`() =
        mainCoroutineRule.runBlockingTest {
            val remindersWithHistory = mutableListOf<List<ReminderAndHistory>>().let {
                fakeReminderRepository.getAllWithTodayHistory().toCollection(it)
            }.first()

            // shuffle test data because initial test data is already ordered correctly
            fakeReminderRepository.deleteAll()
            fakeReminderRepository.saveAll(reminders.shuffled())

            viewModel.changeSortRules(ReminderSortRules(time = SortRule.ASCENDING))
            assertThat(viewModel.reminders.getOrAwaitValue()).containsExactlyElementsIn(
                remindersWithHistory.sortedBy {
                    // test data is set up such that sorting on description can also sort on time,
                    // but the ViewModel uses its own method for sorting that may not be correct
                    it.reminder.description
                }).inOrder()

            viewModel.changeSortRules(ReminderSortRules(time = SortRule.DESCENDING))
            assertThat(viewModel.reminders.getOrAwaitValue()).containsExactlyElementsIn(
                remindersWithHistory.sortedByDescending {
                    it.reminder.description
                }).inOrder()

            viewModel.changeSortRules(ReminderSortRules(description = SortRule.DESCENDING))
            assertThat(viewModel.reminders.getOrAwaitValue()).containsExactlyElementsIn(
                remindersWithHistory.sortedByDescending {
                    it.reminder.description
                }).inOrder()

            viewModel.changeSortRules(ReminderSortRules(description = SortRule.ASCENDING))
            assertThat(viewModel.reminders.getOrAwaitValue()).containsExactlyElementsIn(
                remindersWithHistory.sortedBy {
                    it.reminder.description
                }).inOrder()
        }

    @Test
    fun `reminders list can be both sorted and filtered`() = mainCoroutineRule.runBlockingTest {
        val remindersWithHistory = mutableListOf<List<ReminderAndHistory>>().let {
            fakeReminderRepository.getAllWithTodayHistory().toCollection(it)
        }.first()

        fakeReminderRepository.deleteAll()
        fakeReminderRepository.saveAll(reminders.shuffled())

        // sort by descending time (description "7" should appear first)
        viewModel.changeSortRules(ReminderSortRules(time = SortRule.DESCENDING))
        // filter out one-off reminders, and any that don't occur on mondays
        viewModel.changeFilterRules(
            ReminderFilterRules(
                includeToday = false,
                daysOfWeek = setOf(DayOfWeek.MONDAY)
            )
        )

        assertThat(viewModel.reminders.getOrAwaitValue()).containsExactlyElementsIn(
            remindersWithHistory.filter { it.reminder.dayIsChecked(DayOfWeek.MONDAY) }
                .sortedByDescending {
                    it.reminder.description
                }).inOrder()
    }
}
