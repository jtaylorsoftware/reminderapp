package com.github.jeremyt135.reminderapp.screens.reminder.overview

import com.github.jeremyt135.reminderapp.data.domain.SchedulingRules
import com.github.jeremyt135.reminderapp.data.domain.SchedulingType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ReminderFilterRulesTest {
    @Test
    fun `checkWithinDateRange for REPEATING_FOREVER schedule excludes start dates less than the filter start date`() {
        val schedulingRules = SchedulingRules(
            type = SchedulingType.REPEAT_FOREVER,
            startDate = LocalDate.now()
        )
        val filterRules = ReminderFilterRules(startDate = LocalDate.now().plusDays(3))
        assertThat(filterRules.checkWithinDateRange(schedulingRules)).isFalse()
    }

    @Test
    fun `checkWithinDateRange for REPEATING_FOREVER schedule includes start dates greater or equal to the filter start date`() {
        val schedulingRules = SchedulingRules(
            type = SchedulingType.REPEAT_FOREVER,
            startDate = LocalDate.now().plusDays(3)
        )
        val filterRules = ReminderFilterRules(startDate = LocalDate.now().plusDays(1))
        assertThat(filterRules.checkWithinDateRange(schedulingRules)).isTrue()
    }

    @Test
    fun `checkWithinDateRange for REPEATING schedule excludes end dates greater than the filter end date`() {
        val schedulingRules = SchedulingRules(
            type = SchedulingType.REPEATING,
            endDate = LocalDate.now().plusDays(3)
        )
        val filterRules = ReminderFilterRules(endDate = LocalDate.now().plusDays(1))
        assertThat(filterRules.checkWithinDateRange(schedulingRules)).isFalse()
    }

    @Test
    fun `checkWithinDateRange for REPEATING schedule includes end dates less than or equal to the filter end date`() {
        val schedulingRules = SchedulingRules(
            type = SchedulingType.REPEATING,
            endDate = LocalDate.now().plusDays(1)
        )
        val filterRules = ReminderFilterRules(endDate = LocalDate.now().plusDays(3))
        assertThat(filterRules.checkWithinDateRange(schedulingRules)).isTrue()
    }

    @Test
    fun `checkWithinDateRange for REPEATING schedule includes schedules that have both start and end date within allowed range`() {
        val schedulingRules = SchedulingRules(
            type = SchedulingType.REPEATING,
            startDate = LocalDate.now().plusDays(3),
            endDate = LocalDate.now().plusDays(5)
        )
        val filterRules = ReminderFilterRules(
            startDate = LocalDate.now().plusDays(1),
            endDate = LocalDate.now().plusDays(8)
        )
        assertThat(filterRules.checkWithinDateRange(schedulingRules)).isTrue()
    }

    @Test
    fun `checkWithinDateRange for REPEATING schedule includes schedules where filter start date is inclusive within schedule (start,end) dates`() {
        val schedulingRules = SchedulingRules(
            type = SchedulingType.REPEATING,
            startDate = LocalDate.now().plusDays(3),
            endDate = LocalDate.now().plusDays(9)
        )
        val filterRules = ReminderFilterRules(
            startDate = LocalDate.now().plusDays(4),
            endDate = LocalDate.now().plusDays(10)
        )
        assertThat(filterRules.checkWithinDateRange(schedulingRules)).isTrue()
    }

    @Test
    fun `checkWithinDateRange for REPEATING schedule excludes schedules where filter start date beyond schedule start date`() {
        val schedulingRules = SchedulingRules(
            type = SchedulingType.REPEATING,
            startDate = LocalDate.now().plusDays(3),
            endDate = LocalDate.now().plusDays(9)
        )
        val filterRules = ReminderFilterRules(
            startDate = LocalDate.now().plusDays(10),
            endDate = LocalDate.now().plusDays(12)
        )
        assertThat(filterRules.checkWithinDateRange(schedulingRules)).isFalse()
    }

    @Test
    fun `checkWithinDateRange for REPEATING schedule returns true if both start and end date are null`() {
        val schedulingRules = SchedulingRules(
            type = SchedulingType.REPEATING,
        )
        val filterRules = ReminderFilterRules()
        assertThat(filterRules.checkWithinDateRange(schedulingRules)).isTrue()
    }

    @Test
    fun `checkWithinDateRange for SINGLE schedule returns true if within start date and includesToday`() {
        val schedulingRules = SchedulingRules(
            type = SchedulingType.SINGLE,
            startDate = LocalDate.now(),
        )
        val filterRules = ReminderFilterRules(
            includeToday = true,
            startDate = LocalDate.now()
        )
        assertThat(filterRules.checkWithinDateRange(schedulingRules)).isTrue()
    }

    @Test
    fun `checkWithinDaysOfWeek returns value of includeToday for schedules of type SINGLE`() {
        val schedulingRules = SchedulingRules(
            type = SchedulingType.SINGLE
        )
        var filterRules = ReminderFilterRules(includeToday = false)
        assertThat(filterRules.checkWithinDaysOfWeek(schedulingRules)).isFalse()

        filterRules = ReminderFilterRules(includeToday = true)
        assertThat(filterRules.checkWithinDaysOfWeek(schedulingRules)).isTrue()
    }

    @Test
    fun `checkWithinDaysOfWeek for repeating schedules returns true if schedule has any of the filter days`() {
        var schedulingRules = SchedulingRules(
            type = SchedulingType.REPEATING,
            daysOfWeek = sortedSetOf(DayOfWeek.MONDAY)
        )
        val filterRules =
            ReminderFilterRules(daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))
        assertThat(filterRules.checkWithinDaysOfWeek(schedulingRules)).isTrue()

        schedulingRules = SchedulingRules(
            type = SchedulingType.REPEAT_FOREVER,
            daysOfWeek = sortedSetOf(DayOfWeek.MONDAY)
        )
        assertThat(filterRules.checkWithinDaysOfWeek(schedulingRules)).isTrue()
    }

    @Test
    fun `checkWithinDaysOfWeek for repeating schedules returns false if schedule does not have any of the filter days`() {
        var schedulingRules = SchedulingRules(
            type = SchedulingType.REPEATING,
            daysOfWeek = sortedSetOf(DayOfWeek.WEDNESDAY)
        )
        val filterRules =
            ReminderFilterRules(daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))
        assertThat(filterRules.checkWithinDaysOfWeek(schedulingRules)).isFalse()

        schedulingRules = SchedulingRules(
            type = SchedulingType.REPEAT_FOREVER,
            daysOfWeek = sortedSetOf(DayOfWeek.FRIDAY)
        )
        assertThat(filterRules.checkWithinDaysOfWeek(schedulingRules)).isFalse()
    }
}