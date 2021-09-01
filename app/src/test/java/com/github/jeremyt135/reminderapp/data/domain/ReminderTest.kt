package com.github.jeremyt135.reminderapp.data.domain

import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.github.jeremyt135.reminderapp.util.getFullLocaleDisplay
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAdjusters

class ReminderTest {
    @Test
    fun hourAndMinute_returnsTimeSetInConstructor() {
        val time = LocalTime.now()
        val reminder = Reminder(
            "Description",
            schedulingRules = SchedulingRules(time = time)
        )

        assertThat(reminder.schedulingRules.hour).isEqualTo(time.hour)
        assertThat(reminder.schedulingRules.minute).isEqualTo(time.minute)
    }

    @Test
    fun dayIsChecked_emptySet_returnsFalse() {
        val reminder = Reminder(
            "Description",
        )
        DayOfWeek.values().forEach { day ->
            val dayStr: String = day.toString()
            assertThat(reminder.schedulingRules.dayIsScheduled(dayStr)).isFalse()
        }
    }

    @Test
    fun dayIsChecked_allChecked_returnsTrue() {
        val reminder = Reminder(
            "Description",
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
            "Description",
            schedulingRules = SchedulingRules(daysOfWeek = sortedSetOf(DayOfWeek.MONDAY))
        )
        assertThat(reminder.getDaysDisplayString())
            .isEqualTo("Every ${DayOfWeek.MONDAY.getFullLocaleDisplay()}")
    }

    @Test
    fun datesDisplay_noneChecked_returnsToday() {
        val reminder = Reminder(
            "Description",
        )
        assertThat(reminder.getDaysDisplayString())
            .isEqualTo("Today")
    }

    @Test
    fun datesDisplay_allChecked_returnsPhraseEveryDay() {
        val reminder = Reminder(
            "Description",
            schedulingRules = SchedulingRules(daysOfWeek = DayOfWeek.values().toSortedSet())
        )
        assertThat(reminder.getDaysDisplayString())
            .isEqualTo("Every day")
    }

    @Test
    fun getNextTimeForAlarm_oneOffInPast_returnsNull() {
        val reminder = Reminder(
            schedulingRules = SchedulingRules(
                time = LocalTime.now().minusMinutes(1),
                daysOfWeek = sortedSetOf()
            )
        )
        val nextTime = reminder.schedulingRules.getNextTimeForAlarm(LocalDate.now().dayOfWeek)
        assertThat(nextTime).isNull()
    }

    @Test
    fun getNextTimeForAlarm_oneOffInFuture_returnsTime() {
        val reminder = Reminder(
            schedulingRules = SchedulingRules(
                time = LocalTime.now().plusMinutes(1),
                daysOfWeek = sortedSetOf()
            )
        )
        val nextTime = reminder.schedulingRules.getNextTimeForAlarm(LocalDate.now().dayOfWeek)
        assertThat(nextTime).isNotNull()
    }

    @Test
    fun getNextTimeForAlarm_oneOffAtNow_returnsTimeNow() {
        val reminder = Reminder(
            schedulingRules = SchedulingRules(
                time = LocalTime.now(),
                daysOfWeek = sortedSetOf()
            )
        )
        val nextTime = reminder.schedulingRules.getNextTimeForAlarm(LocalDate.now().dayOfWeek)
        assertThat(nextTime).isNotNull()
    }

    @Test
    fun getNextTimeForAlarm_repeatingTodayNow_returnsTimeNow() {
        val today = LocalDate.now().dayOfWeek
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(
                    time = LocalTime.now(),
                    daysOfWeek = sortedSetOf(today)
                )
            )

        val nextTime = reminder.schedulingRules.getNextTimeForAlarm(today)!!
        assertThat(nextTime.isEqual(currentLocalDateTime())).isTrue()
    }

    @Test
    fun getNextTimeForAlarm_repeatingTodayNow_dontAllowSameDay_returnsTimeNextWeek() {
        val today = LocalDate.now().dayOfWeek
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(
                    time = LocalTime.now(),
                    daysOfWeek = sortedSetOf(today)
                )
            )

        val nextTime = reminder.schedulingRules.getNextTimeForAlarm(today, false)!!
        val expectedTime = currentLocalDateTime().plusDays(7)
        assertThat(nextTime.isEqual(expectedTime)).isTrue()
    }

    @Test
    fun getNextTimeForAlarm_repeatingTodayFuture_returnsTimeLaterToday() {
        val today = LocalDate.now().dayOfWeek
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(
                    time = LocalTime.now().plusMinutes(1),
                    daysOfWeek = sortedSetOf(today)
                )
            )

        val nextTime = reminder.schedulingRules.getNextTimeForAlarm(today)!!
        val expectedTime = currentLocalDateTime().plusMinutes(1)
        assertThat(nextTime.isEqual(expectedTime)).isTrue()
    }

    @Test
    fun getNextTimeForAlarm_repeatingTodayFuture__dontAllowSameDay_returnsTimeNextWeek() {
        val today = LocalDate.now().dayOfWeek
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(
                    time = LocalTime.now().plusMinutes(1),
                    daysOfWeek = sortedSetOf(today)
                )
            )

        val nextTime = reminder.schedulingRules.getNextTimeForAlarm(today, false)!!
        val expectedTime = currentLocalDateTime().plusMinutes(1).plusDays(7)
        assertThat(nextTime.isEqual(expectedTime)).isTrue()
    }

    @Test
    fun getNextTimeForAlarm_repeatingTodayPast_returnsTimeNextWeek() {
        val today = LocalDate.now().dayOfWeek
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(
                    time = LocalTime.now().minusMinutes(1),
                    daysOfWeek = sortedSetOf(today)
                )
            )

        val nextTime = reminder.schedulingRules.getNextTimeForAlarm(today)!!
        val expectedNextTime =
            currentLocalDateTime().with(TemporalAdjusters.next(today)).minusMinutes(1)
        assertThat(nextTime.isEqual(expectedNextTime)).isTrue()
    }

    @Test
    fun getNextTimeForAlarm_repeatingEverydayPast_returnsTimeTomorrow() {
        val daysRange = ChronoField.DAY_OF_WEEK.range()
        val today = LocalDate.now().dayOfWeek
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(time = LocalTime.now().minusMinutes(1),
                    daysOfWeek =
                    (daysRange.minimum..daysRange.maximum).map {
                        DayOfWeek.of(it.toInt())
                    }.toSortedSet()
                )
            )

        val nextTime = reminder.schedulingRules.getNextTimeForAlarm(today)!!
        val expectedNextTime = currentLocalDateTime().minusMinutes(1).plusDays(1)
        assertThat(nextTime.isEqual(expectedNextTime)).isTrue()
    }


    @Test
    fun getNextTimeForAlarm_repeatingEverydayFuture_returnsTimeLaterToday() {
        val daysRange = ChronoField.DAY_OF_WEEK.range()
        val today = LocalDate.now().dayOfWeek
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(time = LocalTime.now().plusMinutes(1),
                    daysOfWeek =
                    (daysRange.minimum..daysRange.maximum).map {
                        DayOfWeek.of(it.toInt())
                    }.toSortedSet()
                )
            )

        val nextTime = reminder.schedulingRules.getNextTimeForAlarm(today)!!
        val expectedNextTime = currentLocalDateTime().plusMinutes(1)
        assertThat(nextTime.isEqual(expectedNextTime)).isTrue()
    }


    @Test
    fun getNextTimeForAlarm_repeatingEverydayFuture_dontAllowSameDay_returnsTimeTomorrow() {
        val daysRange = ChronoField.DAY_OF_WEEK.range()
        val today = LocalDate.now().dayOfWeek
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(time = LocalTime.now().plusMinutes(1),
                    daysOfWeek =
                    (daysRange.minimum..daysRange.maximum).map {
                        DayOfWeek.of(it.toInt())
                    }.toSortedSet()
                )
            )

        val nextTime = reminder.schedulingRules.getNextTimeForAlarm(today, false)!!
        val expectedNextTime = currentLocalDateTime().plusMinutes(1).plusDays(1)
        assertThat(nextTime.isEqual(expectedNextTime)).isTrue()
    }


    @Test
    fun getNextTimeForAlarm_repeatingEverydayNow_returnsTimeNow() {
        val daysRange = ChronoField.DAY_OF_WEEK.range()
        val today = LocalDate.now().dayOfWeek
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(time = LocalTime.now(), daysOfWeek =
                (daysRange.minimum..daysRange.maximum).map {
                    DayOfWeek.of(it.toInt())
                }.toSortedSet()
                )
            )

        val nextTime = reminder.schedulingRules.getNextTimeForAlarm(today)!!
        val expectedNextTime = currentLocalDateTime()
        assertThat(nextTime.isEqual(expectedNextTime)).isTrue()
    }


    @Test
    fun getNextTimeForAlarm_repeatingEverydayNow_dontAllowSameDay_returnsTimeTomorrow() {
        val daysRange = ChronoField.DAY_OF_WEEK.range()
        val today = LocalDate.now().dayOfWeek
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(time = LocalTime.now(), daysOfWeek =
                (daysRange.minimum..daysRange.maximum).map {
                    DayOfWeek.of(it.toInt())
                }.toSortedSet()
                )
            )

        val nextTime = reminder.schedulingRules.getNextTimeForAlarm(today, false)!!
        val expectedNextTime = currentLocalDateTime().plusDays(1)
        assertThat(nextTime.isEqual(expectedNextTime)).isTrue()
    }

    @Test
    fun getNextTimeForAlarm_repeatingEverydayPastTime_todayEndOfWeek_returnsStartOfWeek() {
        val daysRange = ChronoField.DAY_OF_WEEK.range()
        val today = DayOfWeek.of(daysRange.maximum.toInt())
        val startOfWeek = DayOfWeek.of(daysRange.minimum.toInt())
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(time = LocalTime.now().minusMinutes(2),
                    daysOfWeek =
                    (daysRange.minimum..daysRange.maximum).map {
                        DayOfWeek.of(it.toInt())
                    }.toSortedSet()
                )
            )

        val nextTime = reminder.schedulingRules.getNextTimeForAlarm(today)!!
        val expectedNextTime =
            currentLocalDateTime().with(TemporalAdjusters.next(startOfWeek)).minusMinutes(2)
        assertThat(nextTime.isEqual(expectedNextTime)).isTrue()
    }

    @Test
    fun getNextTimeForAlarm_repeatingEverydayPastTime_todayStartOfWeek_returnsNextDay() {
        val daysRange = ChronoField.DAY_OF_WEEK.range()
        val today = DayOfWeek.of(daysRange.minimum.toInt())
        val nextDay = today.plus(1)
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(time = LocalTime.now().minusMinutes(1),
                    daysOfWeek =
                    (daysRange.minimum..daysRange.maximum).map {
                        DayOfWeek.of(it.toInt())
                    }.toSortedSet()
                )
            )

        val nextTime = reminder.schedulingRules.getNextTimeForAlarm(today)!!
        val expectedNextTime =
            currentLocalDateTime().with(TemporalAdjusters.next(nextDay)).minusMinutes(1)
        assertThat(nextTime.isEqual(expectedNextTime)).isTrue()
    }


    @Test
    fun getNextTimeForAlarm_repeatingTwoDaysPastTime_todayLast_returnsFirstDay() {
        val today = DayOfWeek.FRIDAY
        val nextDay = DayOfWeek.MONDAY
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(
                    time = LocalTime.now().minusMinutes(1),
                    daysOfWeek = sortedSetOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)
                )
            )

        val nextTime = reminder.schedulingRules.getNextTimeForAlarm(today)!!
        val expectedNextTime =
            currentLocalDateTime().with(TemporalAdjusters.next(nextDay)).minusMinutes(1)
        assertThat(nextTime.isEqual(expectedNextTime)).isTrue()
    }


    @Test
    fun getNextTimeForAlarm_repeatingTwoDays_todayNotInSet_returnsNextDayAvailable() {
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(
                    time = LocalTime.now(),
                    daysOfWeek = sortedSetOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)
                )
            )

        var today = DayOfWeek.WEDNESDAY
        var nextTime = reminder.schedulingRules.getNextTimeForAlarm(today)!!

        assertThat(nextTime.dayOfWeek).isEqualTo(DayOfWeek.FRIDAY)

        today = DayOfWeek.SATURDAY
        nextTime = reminder.schedulingRules.getNextTimeForAlarm(today)!!

        assertThat(nextTime.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
    }

    @Test
    fun getNextTimeForAlarm_todayMidnight_returnsNull() {
        val today = LocalDate.now().dayOfWeek
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(
                    time = LocalTime.MIDNIGHT,
                    daysOfWeek = sortedSetOf()
                )
            )

        assertThat(reminder.schedulingRules.getNextTimeForAlarm(today)).isNull()
    }


    @Test
    fun getNextTimeForAlarm_today11_59_returnsTime11_59() {
        val today = LocalDate.now().dayOfWeek
        val time = LocalTime.MIDNIGHT.minusMinutes(1)
        val reminder =
            Reminder(
                schedulingRules = SchedulingRules(
                    time = time,
                    daysOfWeek = sortedSetOf()
                )
            )

        val nextTime = reminder.schedulingRules.getNextTimeForAlarm(today)!!
        val expectedNextTime =
            LocalDateTime.of(LocalDate.now(), time)
        assertThat(nextTime.isEqual(expectedNextTime)).isTrue()
    }
}