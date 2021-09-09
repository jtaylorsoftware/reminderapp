package com.github.jeremyt135.reminderapp.screens.reminder.overview

import com.github.jeremyt135.reminderapp.data.domain.SchedulingRules
import com.github.jeremyt135.reminderapp.data.domain.SchedulingType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ReminderFilterRulesTest {
    @Test
    fun `checkWithinDateRange excludes start dates less than the filter start date`() {
        val schedulingRules = SchedulingRules(startDate = LocalDate.now())
        val filterRules = ReminderFilterRules(startDate = LocalDate.now().plusDays(3))
        assertThat(filterRules.checkWithinDateRange(schedulingRules)).isFalse()
    }

    @Test
    fun `checkWithinDateRange includes start dates greater or equal to the filter start date`() {
        val schedulingRules = SchedulingRules(startDate = LocalDate.now().plusDays(3))
        val filterRules = ReminderFilterRules(startDate = LocalDate.now().plusDays(1))
        assertThat(filterRules.checkWithinDateRange(schedulingRules)).isTrue()
    }


    @Test
    fun `checkWithinDateRange excludes end dates greater than the filter end date`() {
        val schedulingRules = SchedulingRules(endDate = LocalDate.now().plusDays(3))
        val filterRules = ReminderFilterRules(startDate = LocalDate.now().plusDays(1))
        assertThat(filterRules.checkWithinDateRange(schedulingRules)).isFalse()
    }

    @Test
    fun `checkWithinDateRange includes end dates less than or equal to the filter end date`() {
        val schedulingRules = SchedulingRules(endDate = LocalDate.now().plusDays(1))
        val filterRules = ReminderFilterRules(endDate = LocalDate.now().plusDays(3))
        assertThat(filterRules.checkWithinDateRange(schedulingRules)).isTrue()
    }

    @Test
    fun `checkWithinDateRange includes schedules that have both start and end date within allowed range`() {
        val schedulingRules = SchedulingRules(
            startDate = LocalDate.now().plusDays(3),
            endDate = LocalDate.now().plusDays(1)
        )
        val filterRules = ReminderFilterRules(
            startDate = LocalDate.now().plusDays(1),
            endDate = LocalDate.now().plusDays(3)
        )
        assertThat(filterRules.checkWithinDateRange(schedulingRules)).isTrue()
    }

    @Test
    fun `checkWithinDateRange returns true if both start and end date are null`(){
        val schedulingRules = SchedulingRules(
            startDate = LocalDate.now().plusDays(3),
            endDate = LocalDate.now().plusDays(1)
        )
        val filterRules = ReminderFilterRules()
        assertThat(filterRules.checkWithinDateRange(schedulingRules)).isTrue()
    }

    @Test
    fun `checkWithinDaysOfWeek returns value of includeToday for schedules of type SINGLE`(){
        val schedulingRules = SchedulingRules(
            type = SchedulingType.SINGLE
        )
        var filterRules = ReminderFilterRules(includeToday = false)
        assertThat(filterRules.checkWithinDaysOfWeek(schedulingRules)).isFalse()

        filterRules = ReminderFilterRules(includeToday = true)
        assertThat(filterRules.checkWithinDaysOfWeek(schedulingRules)).isTrue()
    }

    @Test
    fun `checkWithinDaysOfWeek for repeating schedules returns true if schedule has any of the filter days`(){
        var schedulingRules = SchedulingRules(
            type = SchedulingType.REPEATING,
            daysOfWeek = sortedSetOf(DayOfWeek.MONDAY)
        )
        val filterRules = ReminderFilterRules(daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))
        assertThat(filterRules.checkWithinDaysOfWeek(schedulingRules)).isTrue()

        schedulingRules = SchedulingRules(
            type = SchedulingType.REPEAT_FOREVER,
            daysOfWeek = sortedSetOf(DayOfWeek.MONDAY)
        )
        assertThat(filterRules.checkWithinDaysOfWeek(schedulingRules)).isTrue()
    }

    @Test
    fun `checkWithinDaysOfWeek for repeating schedules returns false if schedule does not have any of the filter days`(){
        var schedulingRules = SchedulingRules(
            type = SchedulingType.REPEATING,
            daysOfWeek = sortedSetOf(DayOfWeek.WEDNESDAY)
        )
        val filterRules = ReminderFilterRules(daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))
        assertThat(filterRules.checkWithinDaysOfWeek(schedulingRules)).isFalse()

        schedulingRules = SchedulingRules(
            type = SchedulingType.REPEAT_FOREVER,
            daysOfWeek = sortedSetOf(DayOfWeek.FRIDAY)
        )
        assertThat(filterRules.checkWithinDaysOfWeek(schedulingRules)).isFalse()
    }
}