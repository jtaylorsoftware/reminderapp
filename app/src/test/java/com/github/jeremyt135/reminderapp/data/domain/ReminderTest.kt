package com.github.jeremyt135.reminderapp.data.domain

import com.github.jeremyt135.reminderapp.util.getFullLocaleDisplay
import com.google.common.truth.Truth.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ReminderTest {
    @Test
    fun hourAndMinute_returnsTimeSetInConstructor() {
        val time = LocalTime.now()
        val reminder = Reminder(
            "Description",
            ReminderStatus.PENDING,
            time
        )

        assertThat(reminder.hour).isEqualTo(time.hour)
        assertThat(reminder.minute).isEqualTo(time.minute)
    }

    @Test
    fun dayIsChecked_emptySet_returnsFalse() {
        val reminder = Reminder(
            "Description",
            daysOfWeek = sortedSetOf()
        )
        DayOfWeek.values().forEach { day ->
            val dayStr: String = day.toString()
            assertThat(reminder.dayIsChecked(dayStr)).isFalse()
        }
    }

    @Test
    fun dayIsChecked_allChecked_returnsTrue() {
        val reminder = Reminder(
            "Description",
            daysOfWeek = DayOfWeek.values().toSortedSet()
        )
        DayOfWeek.values().forEach { day ->
            val dayStr: String = day.toString()
            assertThat(reminder.dayIsChecked(dayStr)).isTrue()
        }
    }

    @Test
    fun datesDisplay_someChecked_returnsEveryLocaleStrings() {
        val reminder = Reminder(
            "Description",
            daysOfWeek = sortedSetOf(DayOfWeek.MONDAY)
        )
        assertThat(reminder.getDaysDisplayString())
            .isEqualTo("Every ${DayOfWeek.MONDAY.getFullLocaleDisplay()}")
    }

    @Test
    fun datesDisplay_noneChecked_returnsToday() {
        val reminder = Reminder(
            "Description",
            daysOfWeek = sortedSetOf()
        )
        assertThat(reminder.getDaysDisplayString())
            .isEqualTo("Today")
    }

    @Test
    fun datesDisplay_allChecked_returnsPhraseEveryDay() {
        val reminder = Reminder(
            "Description",
            daysOfWeek = DayOfWeek.values().toSortedSet()
        )
        assertThat(reminder.getDaysDisplayString())
            .isEqualTo("Every day")
    }

    @Test
    fun isComplete_pending_returnsFalse() {
        val reminder = Reminder(
            status = ReminderStatus.PENDING
        )
        assertThat(reminder.isComplete).isFalse()
    }

    @Test
    fun isComplete_notPending_returnsTrue() {
        val reminder = Reminder(
            status = ReminderStatus.SKIPPED
        )

        assertThat(reminder.isComplete).isTrue()

        reminder.status = ReminderStatus.COMPLETED

        assertThat(reminder.isComplete).isTrue()
    }

    @Test
    fun alarmShouldBeSetToday_isTodayNotComplete_returnsTrue() {
        val reminder = Reminder(
            status = ReminderStatus.PENDING
        )

        assertThat(reminder.alarmCanBeSetToday(LocalDate.now().dayOfWeek)).isTrue()

        reminder.daysOfWeek.add(DayOfWeek.MONDAY)

        assertThat(reminder.alarmCanBeSetToday(DayOfWeek.MONDAY)).isTrue()
    }

    @Test
    fun alarmShouldBeSetToday_notTodayNotComplete_returnsFalse() {
        val reminder = Reminder(
            status = ReminderStatus.PENDING,
            daysOfWeek = sortedSetOf(DayOfWeek.WEDNESDAY)
        )
        val today = DayOfWeek.MONDAY

        assertThat(reminder.alarmCanBeSetToday(today)).isFalse()
    }

    @Test
    fun alarmShouldBeSetToday_completeIsToday_returnsFalse(){
        fun testWithStatus(status: ReminderStatus){
            val reminder = Reminder(
                status = status
            )

            assertThat(reminder.alarmCanBeSetToday(LocalDate.now().dayOfWeek)).isFalse()

            reminder.daysOfWeek.add(DayOfWeek.MONDAY)

            assertThat(reminder.alarmCanBeSetToday(DayOfWeek.MONDAY)).isFalse()
        }

        testWithStatus(ReminderStatus.COMPLETED)
        testWithStatus(ReminderStatus.SKIPPED)
    }
}