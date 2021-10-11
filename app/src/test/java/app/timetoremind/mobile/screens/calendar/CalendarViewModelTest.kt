package app.timetoremind.mobile.screens.calendar

import app.timetoremind.mobile.MainCoroutineRule
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.data.domain.ReminderAndHistory
import app.timetoremind.mobile.data.domain.SchedulingRules
import app.timetoremind.mobile.data.domain.SchedulingType
import app.timetoremind.mobile.getOrAwaitValue
import app.timetoremind.mobile.screens.reminder.calendar.CalendarAdapter
import app.timetoremind.mobile.screens.reminder.calendar.CalendarDateItem
import app.timetoremind.mobile.screens.reminder.calendar.CalendarViewModel
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import java.time.YearMonth
import javax.inject.Inject

@HiltAndroidTest
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class CalendarViewModelTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var testScope: MainCoroutineRule

    private lateinit var viewModel: CalendarViewModel

    @Before
    fun setup() {
        hiltRule.inject()

        val reminders = listOf(
            Reminder(schedulingRules = SchedulingRules(startDate = LocalDate.now().plusDays(1))),
            Reminder(
                schedulingRules = SchedulingRules(
                    type = SchedulingType.REPEAT_FOREVER,
                    daysOfWeek = DayOfWeek.values().toSortedSet(),
                    startDate = LocalDate.now().plusDays(1)
                )
            ),
            Reminder(schedulingRules = SchedulingRules(startDate = LocalDate.now().plusDays(3))),
            Reminder(
                schedulingRules = SchedulingRules(
                    type = SchedulingType.REPEAT_FOREVER,
                    daysOfWeek = DayOfWeek.values().toSortedSet(),
                    startDate = LocalDate.now().plusDays(4)
                )
            ),
            Reminder()
        )

        runBlocking {
            reminderRepository.deleteAll()
            reminderRepository.saveAll(reminders)
        }

        viewModel = CalendarViewModel(
            RuntimeEnvironment.getApplication(),
            reminderRepository,
            testScope.dispatcher
        )
    }

    @Test
    fun initialYearMonth_isCurrentMonth() = testScope.runBlockingTest {
        assertThat(viewModel.selectedYearMonth.value).isEqualTo(YearMonth.now())
    }

    @Test
    fun yearMonthString_hasShortFormat() = testScope.runBlockingTest {
        var monthString = YearMonth.now().month.toString()
        monthString = monthString[0] + monthString.substring(1).lowercase()
        val expected = "${monthString} ${YearMonth.now().year.toString()}"
        assertThat(viewModel.selectedYearMonthString.getOrAwaitValue()).isEqualTo(expected)
    }

    @Test
    fun goToPreviousMonth_doesNotDecreaseMonth_ifOnCurrentMonth() = testScope.runBlockingTest {
        viewModel.goToPreviousMonth()
        assertThat(viewModel.selectedYearMonth.value).isEqualTo(YearMonth.now())
    }

    @Test
    fun goToPreviousMonth_decreasesMonthByOne() = testScope.runBlockingTest {
        // Forward by a month so not on current
        viewModel.goToNextMonth()
        assertThat(viewModel.selectedYearMonth.value).isEqualTo(YearMonth.now().plusMonths(1))
        // Go back
        viewModel.goToPreviousMonth()
        assertThat(viewModel.selectedYearMonth.value).isEqualTo(YearMonth.now())
    }

    @Test
    fun goToNextMonth_increasesMonthByOne() = testScope.runBlockingTest {
        viewModel.goToNextMonth()
        assertThat(viewModel.selectedYearMonth.value).isEqualTo(YearMonth.now().plusMonths(1))
    }

    @Test
    fun setSelectedDate_changesSelectedDate() = testScope.runBlockingTest {
        val expectedDate = LocalDate.now().plusDays(10)
        viewModel.setSelectedDate(expectedDate)
        assertThat(viewModel.selectedDate.getOrAwaitValue()).isEqualTo(expectedDate)
    }


    @Test
    fun remindersOnSelectedDate_initiallyNullOrEmpty() = testScope.runBlockingTest {
        viewModel.refreshData()
        val reminders = viewModel.remindersOnSelectedDate.getOrAwaitValue(time = 10)
        assertThat(reminders.isNullOrEmpty()).isTrue()
    }

    @Test
    fun remindersOnSelectedDate_containsOnlyRemindersOnDate() = testScope.runBlockingTest {
        val selectedDate = LocalDate.now().plusDays(3)
        viewModel.refreshData()
        viewModel.setSelectedDate(selectedDate)
        val reminders = viewModel.remindersOnSelectedDate.getOrAwaitValue(time = 10)
        val expectedRemindersOnDate = reminderRepository.getAll().first().filter {
            when (it.schedulingRules.type) {
                SchedulingType.SINGLE -> {
                    it.schedulingRules.startDate == selectedDate
                }
                SchedulingType.REPEATING -> {
                    it.schedulingRules.endDate < selectedDate
                }
                else -> {
                    it.schedulingRules.startDate <= selectedDate
                }
            }
        }.map { ReminderAndHistory(it, null) }
        assertThat(reminders).hasSize(expectedRemindersOnDate.size)
        reminders!!.forEach {
            assertThat(it.data).isIn(expectedRemindersOnDate)
        }
    }

    @Test
    fun calendarData_nullUntilRefreshed() = runBlocking {
        assertThat(viewModel.calendarData.getOrAwaitValue()).isNull()
        viewModel.refreshData()
        assertThat(viewModel.calendarData.getOrAwaitValue()).isNotNull()
    }

    @Test
    fun calendarData_containsDatesForCurrentAndAdjacentMonths() = runBlocking {
        val currentMonth = YearMonth.now()
        val daysInCurrentMonth = currentMonth.lengthOfMonth()
        val firstDayOfMonth = currentMonth.atDay(1).dayOfWeek.value % 7

        val daysInPreviousMonth = currentMonth.minusMonths(1).lengthOfMonth()

        viewModel.calendarData.getOrAwaitValue()
        viewModel.refreshData()
        val calendarData = viewModel.calendarData.getOrAwaitValue()
        assertThat(calendarData).isNotNull()
        assertThat(calendarData!!).hasSize(CalendarAdapter.ITEM_COUNT)
        (7 until CalendarAdapter.ITEM_COUNT).forEach { position ->
            val dayOfMonth = (calendarData[position] as CalendarDateItem).date!!.dayOfMonth
            val firstPosition = 7 + firstDayOfMonth
            when {
                position < firstPosition -> {
                    assertThat(dayOfMonth).isEqualTo(daysInPreviousMonth - firstPosition + position + 1)
                }
                position in (firstPosition until firstPosition + daysInCurrentMonth) -> {
                    assertThat(dayOfMonth).isEqualTo(position - firstPosition + 1)
                }
                else -> {
                    assertThat(dayOfMonth).isEqualTo(position - (firstPosition + daysInCurrentMonth - 1))
                }
            }
        }
    }
}