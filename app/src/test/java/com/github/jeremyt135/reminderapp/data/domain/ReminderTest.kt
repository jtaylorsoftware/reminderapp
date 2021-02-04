package com.github.jeremyt135.reminderapp.data.domain

import com.github.jeremyt135.reminderapp.util.getFullLocaleDisplay
import com.google.common.truth.Truth.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ReminderTest {
    @Test
    fun hourAndMinute_seturnsTimeSetInConstructor() {
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
            daysOfWeek = mutableSetOf()
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
            daysOfWeek = DayOfWeek.values().toMutableSet()
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
            daysOfWeek = mutableSetOf(DayOfWeek.MONDAY)
        )
        assertThat(reminder.getDaysDisplayString())
            .isEqualTo("Every ${DayOfWeek.MONDAY.getFullLocaleDisplay()}")
    }

    @Test
    fun datesDisplay_noneChecked_returnsToday() {
        val reminder = Reminder(
            "Description",
            daysOfWeek = mutableSetOf()
        )
        assertThat(reminder.getDaysDisplayString())
            .isEqualTo("Today")
    }

    @Test
    fun datesDisplay_allChecked_returnsPhraseEveryDay() {
        val reminder = Reminder(
            "Description",
            daysOfWeek = DayOfWeek.values().toMutableSet()
        )
        assertThat(reminder.getDaysDisplayString())
            .isEqualTo("Every day")
    }
}