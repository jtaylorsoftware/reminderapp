package com.github.jeremyt135.reminderapp.data.domain

import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.github.jeremyt135.reminderapp.util.currentLocalTime
import com.github.jeremyt135.reminderapp.util.getFullLocaleDisplay
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

class ReminderTest {
    @Test
    fun hourAndMinute_returnsTimeSetInConstructor() {
        val time = LocalTime.now()
        val reminder = Reminder(
            schedulingRules = SchedulingRules(time = time)
        )

        assertThat(reminder.schedulingRules.hour).isEqualTo(time.hour)
        assertThat(reminder.schedulingRules.minute).isEqualTo(time.minute)
    }

    @Test
    fun dayIsChecked_emptySet_returnsFalse() {
        val reminder = Reminder(schedulingRules = SchedulingRules(daysOfWeek = sortedSetOf()))
        DayOfWeek.values().forEach { day ->
            val dayStr: String = day.toString()
            assertThat(reminder.schedulingRules.dayIsScheduled(dayStr)).isFalse()
        }
    }

    @Test
    fun dayIsChecked_allChecked_returnsTrue() {
        val reminder = Reminder(
            schedulingRules = SchedulingRules(daysOfWeek = DayOfWeek.values().toSortedSet())
        )
        DayOfWeek.values().forEach { day ->
            val dayStr: String = day.toString()
            assertThat(reminder.schedulingRules.dayIsScheduled(dayStr)).isTrue()
        }
    }

    @Test
    fun datesDisplay_someChecked_returnsEveryLocaleStrings() {
        val reminder = Reminder(
            schedulingRules = SchedulingRules(daysOfWeek = sortedSetOf(DayOfWeek.MONDAY))
        )
        assertThat(reminder.getDaysDisplayString())
            .isEqualTo("Every ${DayOfWeek.MONDAY.getFullLocaleDisplay()}")
    }

    @Test
    fun datesDisplay_noneChecked_returnsToday() {
        val reminder = Reminder(schedulingRules = SchedulingRules(daysOfWeek = sortedSetOf()))
        assertThat(reminder.getDaysDisplayString())
            .isEqualTo("No days selected")
    }

    @Test
    fun datesDisplay_allChecked_returnsPhraseEveryDay() {
        val reminder = Reminder(
            schedulingRules = SchedulingRules(daysOfWeek = DayOfWeek.values().toSortedSet())
        )
        assertThat(reminder.getDaysDisplayString())
            .isEqualTo("Every day")
    }

    @Test
    fun getNextTimeForAlarm_single_returnsStartDateWithTime() {
        val date = LocalDate.now().plusDays(1)
        val time = LocalTime.now().plusHours(2)
        val reminder = Reminder(
            schedulingRules = SchedulingRules(
                type = SchedulingType.SINGLE,
                startDate = date,
                time = time
            )
        )
        assertThat(reminder.schedulingRules.getNextTimeForAlarm(currentLocalDateTime())).isEqualTo(
            date.atTime(time).truncatedTo(ChronoUnit.MINUTES)
        )
    }

    @Test
    fun getNextTimeForAlarm_single_timeInPast_allowPast_returnsTime() {
        val date = LocalDate.now().minusDays(1)
        val time = LocalTime.now().minusHours(2)
        val reminder = Reminder(
            schedulingRules = SchedulingRules(
                type = SchedulingType.SINGLE,
                startDate = date,
                time = time
            )
        )
        assertThat(
            reminder.schedulingRules.getNextTimeForAlarm(
                currentLocalDateTime(),
                nextTimeOptions = SchedulingRules.NextTimeOptions(allowTimeInPast = true)
            )
        ).isEqualTo(
            date.atTime(time).truncatedTo(ChronoUnit.MINUTES)
        )
    }

    @Test
    fun getNextTimeForAlarm_single_timeInPast_DoNotAllowPast_returnsNull() {
        val date = LocalDate.now().minusDays(1)
        val time = LocalTime.now().minusHours(2)
        val reminder = Reminder(
            schedulingRules = SchedulingRules(
                type = SchedulingType.SINGLE,
                startDate = date,
                time = time
            )
        )
        assertThat(
            reminder.schedulingRules.getNextTimeForAlarm(
                currentLocalDateTime(),
                nextTimeOptions = SchedulingRules.NextTimeOptions(allowTimeInPast = false)
            )
        ).isNull()
    }

    @Test
    fun getNextTimeForAlarm_repeating_pastEndDate_returnsNull() {
        // test value for "today's date"
        val today = currentLocalDateTime().plusDays(3)
        val endDate = currentLocalDateTime().toLocalDate().plusDays(1)
        val reminder = Reminder(
            schedulingRules = SchedulingRules(
                type = SchedulingType.REPEATING,
                endDate = endDate,
                daysOfWeek = DayOfWeek.values().toSortedSet()
            )
        )
        assertThat(reminder.schedulingRules.getNextTimeForAlarm(today)).isEqualTo(null)
    }

    @Test
    fun getNextTimeForAlarm_repeating_startDateInFuture_returnsNextWeek() {
        val dayOfWeek = DayOfWeek.MONDAY
        // adjust test date to be a monday
        val startDate =
            currentLocalDateTime().with(TemporalAdjusters.next(dayOfWeek)).toLocalDate()
        val time = currentLocalTime()
        val reminder = Reminder(
            schedulingRules = SchedulingRules(
                type = SchedulingType.REPEATING,
                startDate = startDate,
                time = time,
                daysOfWeek = sortedSetOf(dayOfWeek)
            )
        )
        assertThat(reminder.schedulingRules.getNextTimeForAlarm(currentLocalDateTime())).isEqualTo(
            startDate.atTime(time).with(TemporalAdjusters.nextOrSame(dayOfWeek))
        )
    }

    @Test
    fun getNextTimeForAlarm_repeating_startDateToday_timeInFuture_allowToday_returnsToday() {
        val startDate = currentLocalDateTime().toLocalDate()
        val dayOfWeek = startDate.dayOfWeek
        val time = currentLocalTime().plusMinutes(30)
        val reminder = Reminder(
            schedulingRules = SchedulingRules(
                type = SchedulingType.REPEATING,
                startDate = startDate,
                time = time,
                daysOfWeek = sortedSetOf(dayOfWeek)
            )
        )
        assertThat(
            reminder.schedulingRules.getNextTimeForAlarm(
                currentLocalDateTime(),
                SchedulingRules.NextTimeOptions(allowSameDay = true)
            )
        ).isEqualTo(
            startDate.atTime(time)
        )
    }

    @Test
    fun getNextTimeForAlarm_repeating_startDateToday_timeInFuture_doNotAllowToday_returnsNextWeek() {
        val startDate = currentLocalDateTime().toLocalDate()
        val dayOfWeek = startDate.dayOfWeek
        val time = currentLocalTime().plusMinutes(30)
        val reminder = Reminder(
            schedulingRules = SchedulingRules(
                type = SchedulingType.REPEATING,
                startDate = startDate,
                time = time,
                daysOfWeek = sortedSetOf(dayOfWeek)
            )
        )
        assertThat(
            reminder.schedulingRules.getNextTimeForAlarm(
                currentLocalDateTime(),
                SchedulingRules.NextTimeOptions.ALLOW_NONE
            )
        ).isEqualTo(
            startDate.atTime(time).with(TemporalAdjusters.next(dayOfWeek))
        )
    }

    @Test
    fun getNextTimeForAlarm_repeating_startDateToday_timeInPast_returnsNextDayOfWeek() {
        val today = currentLocalDateTime().toLocalDate()
        val tomorrow = today.plusDays(1)
        val time = currentLocalTime().minusMinutes(30)
        val reminder = Reminder(
            schedulingRules = SchedulingRules(
                type = SchedulingType.REPEATING,
                startDate = today,
                time = time,
                daysOfWeek = sortedSetOf(today.dayOfWeek, tomorrow.dayOfWeek)
            )
        )
        assertThat(reminder.schedulingRules.getNextTimeForAlarm(currentLocalDateTime())).isEqualTo(
            tomorrow.atTime(time)
        )
    }


    @Test
    fun getNextTimeForAlarm_repeating_startsToday_timeInPast_MultipleDays_returnsNextDay() {
        // adjust "today" to be sunday
        val today = currentLocalDateTime().toLocalDate()
        val time = currentLocalTime().minusMinutes(30)
        val days = sortedSetOf(
            today.dayOfWeek,
            DayOfWeek.of((today.dayOfWeek.value + 1) % DayOfWeek.values().size)
        )
        val reminder = Reminder(
            schedulingRules = SchedulingRules(
                type = SchedulingType.REPEATING,
                startDate = today,
                time = time,
                daysOfWeek = days
            )
        )
        assertThat(reminder.schedulingRules.getNextTimeForAlarm(currentLocalDateTime())).isEqualTo(
            today.atTime(time)
                .with(TemporalAdjusters.nextOrSame(days.firstOrNull { it > today.dayOfWeek }
                    ?: days.first()))
        )
    }

    @Test
    fun getNextTimeForAlarm_repeating_startDateToday_timeInPast_OnlyOneDay_returnsNextWeek() {
        val today = currentLocalDateTime().toLocalDate()
        val time = currentLocalTime().minusMinutes(30)
        val reminder = Reminder(
            schedulingRules = SchedulingRules(
                type = SchedulingType.REPEATING,
                startDate = today,
                time = time,
                daysOfWeek = sortedSetOf(today.dayOfWeek)
            )
        )
        assertThat(reminder.schedulingRules.getNextTimeForAlarm(currentLocalDateTime())).isEqualTo(
            today.atTime(time).with(TemporalAdjusters.next(today.dayOfWeek))
        )
    }
}