package com.github.jeremyt135.reminderapp.screens.reminder.overview

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.github.jeremyt135.reminderapp.MainCoroutineRule
import com.github.jeremyt135.reminderapp.broadcast.cancelAlarmIntentsForReminder
import com.github.jeremyt135.reminderapp.data.*
import com.github.jeremyt135.reminderapp.data.domain.*
import com.github.jeremyt135.reminderapp.data.local.FakeHistoryLocalDataSource
import com.github.jeremyt135.reminderapp.service.AlarmService
import com.github.jeremyt135.reminderapp.settings.getBaseSharedPreferences
import com.github.jeremyt135.reminderapp.util.currentEpochMilli
import com.github.jeremyt135.reminderapp.util.uuid
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
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
        doSetup()
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
        assertThat(awaitReminders()).containsExactlyElementsIn(
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
            AlarmActionHandler.createForScheduleNext(any(), any())
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

            assertThat(awaitReminders()).containsExactlyElementsIn(
                reminders.slice(2 until reminders.size).map { ReminderAndHistory(it, null) }
            )

            viewModel.changeFilterRules(ReminderFilterRules(reminderStatuses = setOf(ReminderStatus.COMPLETED)))
            assertThat(awaitReminders()).containsExactlyElementsIn(
                listOf(
                    ReminderAndHistory(reminders[0], completedEntry)
                )
            )

            viewModel.changeFilterRules(ReminderFilterRules(reminderStatuses = setOf(ReminderStatus.SKIPPED)))
            assertThat(awaitReminders()).containsExactlyElementsIn(
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
            assertThat(awaitReminders()).containsExactlyElementsIn(
                remindersWithHistory
            )

            // Should only contain MONDAY
            viewModel.changeFilterRules(
                ReminderFilterRules(
                    includeToday = false,
                    daysOfWeek = setOf(DayOfWeek.MONDAY)
                )
            )
            assertThat(awaitReminders()).containsExactlyElementsIn(
                remindersWithHistory.filter { it.reminder.schedulingRules.dayIsScheduled(DayOfWeek.MONDAY) })

            // Should only contain with FRIDAY
            viewModel.changeFilterRules(
                ReminderFilterRules(
                    includeToday = false,
                    daysOfWeek = setOf(DayOfWeek.FRIDAY)
                )
            )
            assertThat(awaitReminders()).containsExactlyElementsIn(
                remindersWithHistory.filter { it.reminder.schedulingRules.dayIsScheduled(DayOfWeek.FRIDAY) })

            // Should only contain reminders scheduled for today
            viewModel.changeFilterRules(
                ReminderFilterRules(
                    includeToday = true,
                )
            )
            assertThat(awaitReminders()).containsExactlyElementsIn(
                remindersWithHistory.filter {
                    it.reminder.schedulingRules.daysOfWeek.contains(
                        LocalDate.now().dayOfWeek
                    )
                })
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
            assertThat(awaitReminders()).containsExactlyElementsIn(
                remindersWithHistory.sortedBy {
                    // test data is set up such that sorting on description can also sort on time,
                    // but the ViewModel uses its own method for sorting that may not be correct
                    it.reminder.description
                }).inOrder()

            viewModel.changeSortRules(ReminderSortRules(time = SortRule.DESCENDING))
            assertThat(awaitReminders()).containsExactlyElementsIn(
                remindersWithHistory.sortedByDescending {
                    it.reminder.description
                }).inOrder()

            viewModel.changeSortRules(ReminderSortRules(description = SortRule.DESCENDING))
            assertThat(awaitReminders()).containsExactlyElementsIn(
                remindersWithHistory.sortedByDescending {
                    it.reminder.description
                }).inOrder()

            viewModel.changeSortRules(ReminderSortRules(description = SortRule.ASCENDING))
            assertThat(awaitReminders()).containsExactlyElementsIn(
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

        // Filter out single and any reminders not on monday
        viewModel.changeFilterRules(
            ReminderFilterRules(
                includeToday = false,
                daysOfWeek = setOf(DayOfWeek.MONDAY)
            )
        )
        var expectedReminders =
            remindersWithHistory.filter { it.reminder.schedulingRules.dayIsScheduled(DayOfWeek.MONDAY) }

        assertThat(awaitReminders()).containsExactlyElementsIn(expectedReminders)

        expectedReminders =
            expectedReminders.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.reminder.description })

        // Do sorting on filtered list, should still be same size
        viewModel.changeSortRules(ReminderSortRules(description = SortRule.DESCENDING))
        assertThat(awaitReminders()).containsExactlyElementsIn(expectedReminders)
            .inOrder()
    }

    // Waits for reminder data to settle as a non-null value
    private suspend fun awaitReminders(
        timeoutMs: Long = 1000
    ): List<ReminderAndHistory> {
        var remindersData: List<ReminderAndHistory> = emptyList()
        var job: Job? = null
        job = CoroutineScope(Dispatchers.Main).launch {
            withTimeout(timeoutMs) {
                viewModel._reminders.collect {
                    yield()
                    if (!it.isNullOrEmpty()) {
                        remindersData = it
                        job?.cancel()
                    }
                }
            }
        }
        return try {
            job.join()
            remindersData
        } catch(e: CancellationException) {
            // cancelled
            listOf(ReminderAndHistory(Reminder(), null))
        }
    }

    private fun doSetup() {
        val today = LocalDate.now().dayOfWeek
        reminders = listOf(
            Reminder(
                description = "1",
                schedulingRules = SchedulingRules(
                    time = LocalTime.now().plusMinutes(1),
                    type = SchedulingType.REPEAT_FOREVER,
                    daysOfWeek = DayOfWeek.values().toSortedSet()
                )
            ),
            Reminder(
                description = "2",
                schedulingRules = SchedulingRules(
                    time = LocalTime.now().plusMinutes(2),
                    type = SchedulingType.REPEAT_FOREVER,
                    daysOfWeek = DayOfWeek.values().toSortedSet()
                )
            ),
            Reminder(
                description = "3",
                schedulingRules = SchedulingRules(
                    time = LocalTime.now().plusMinutes(3),
                    type = SchedulingType.REPEAT_FOREVER,
                    daysOfWeek = DayOfWeek.values().toSortedSet()
                )
            ),
            Reminder(
                description = "4",
                schedulingRules = SchedulingRules(
                    daysOfWeek = sortedSetOf(today, DayOfWeek.TUESDAY),
                    time = LocalTime.now().plusMinutes(4),
                    type = SchedulingType.REPEAT_FOREVER,
                )
            ),
            Reminder(
                description = "5",
                schedulingRules = SchedulingRules(
                    time = LocalTime.now().plusMinutes(5),
                    type = SchedulingType.REPEAT_FOREVER,
                    daysOfWeek = DayOfWeek.values().toSortedSet()
                )
            ),
            Reminder(
                description = "6",
                schedulingRules = SchedulingRules(
                    daysOfWeek = sortedSetOf(today, DayOfWeek.FRIDAY),
                    time = LocalTime.now().plusMinutes(6),
                    type = SchedulingType.REPEAT_FOREVER
                )
            ),
            Reminder(
                description = "7",
                schedulingRules = SchedulingRules(
                    daysOfWeek = sortedSetOf(today),
                    time = LocalTime.now().plusMinutes(7),
                    type = SchedulingType.REPEAT_FOREVER
                )
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

        mockkObject(AlarmService.Companion)
        justRun { AlarmService.requestStop(any()) }
        mockkObject(AlarmActionHandler.Companion)
        every { AlarmActionHandler.createForAction(any(), any()) } returns mockk(relaxed = true)
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns mockk(relaxed = true)
        mockkStatic(::cancelAlarmIntentsForReminder)
        justRun { cancelAlarmIntentsForReminder(any(), any()) }

        mockkStatic(Context::getBaseSharedPreferences)
        val context = ApplicationProvider.getApplicationContext<Application>()
        val testSharedPreferences = context.getSharedPreferences(uuid(), Context.MODE_PRIVATE)
        every { RuntimeEnvironment.getApplication().applicationContext.getBaseSharedPreferences() } returns testSharedPreferences

        viewModel = RemindersViewModel(
            RuntimeEnvironment.getApplication(),
            fakeReminderRepository,
            fakeAlarmRepository,
            mainCoroutineRule.dispatcher,
            mainCoroutineRule.dispatcher,
            mainCoroutineRule
        )
    }
}
