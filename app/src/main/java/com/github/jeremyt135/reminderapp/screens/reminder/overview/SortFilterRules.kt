package com.github.jeremyt135.reminderapp.screens.reminder.overview

import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.data.domain.SchedulingRules
import com.github.jeremyt135.reminderapp.data.domain.SchedulingType
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Rules for filtering a list of Reminders
 */
data class ReminderFilterRules(
    /**
     * Include one-day Reminders.
     */
    val includeToday: Boolean = true,

    /**
     * The DayOfWeek values to include.
     */
    val daysOfWeek: Set<DayOfWeek> = DayOfWeek.values().toSet(),

    /**
     * The ReminderStatus values to include.
     */
    val reminderStatuses: Set<ReminderStatus> = ReminderStatus.values().toSet(),

    /**
     * The date that Reminders must begin on or after
     */
    val startDate: LocalDate? = null,

    /**
     * The date that Reminders must end on or before
     */
    val endDate: LocalDate? = null
) {
    /**
     * Determines if the schedule falls within the filter's allowed
     * start and end dates. Considers SchedulingType of the schedule.
     * @param schedulingRules Schedule to check
     * @return True if schedule is within the filter range
     */
    fun checkWithinDateRange(schedulingRules: SchedulingRules): Boolean {
        return when (schedulingRules.type) {
            SchedulingType.SINGLE -> {
                val start = startDate ?: schedulingRules.startDate
                val end = endDate ?: schedulingRules.endDate
                includeToday && start <= schedulingRules.startDate && schedulingRules.startDate <= end
            }
            SchedulingType.REPEATING -> {
                val start = startDate ?: schedulingRules.endDate
                val end = endDate ?: schedulingRules.startDate
                start <= schedulingRules.endDate && end >= schedulingRules.startDate
            }
            SchedulingType.REPEAT_FOREVER -> {
                val end = endDate ?: schedulingRules.startDate
                end >= schedulingRules.startDate
            }
        }
    }

    /**
     * Determines if the schedule falls within the filter's allowed
     * daysOfWeek. Considers SchedulingType of the schedule.
     * @param schedulingRules Schedule to check
     * @returns True if schedule is within the allowed daysOfWeek
     */
    fun checkWithinDaysOfWeek(schedulingRules: SchedulingRules): Boolean =
        when (schedulingRules.type) {
            SchedulingType.SINGLE -> includeToday
            SchedulingType.REPEATING, SchedulingType.REPEAT_FOREVER -> schedulingRules.daysOfWeek.any { day -> day in daysOfWeek }
        }
}

/**
 * Rules for sorting a list of Reminders
 */
data class ReminderSortRules(
    /**
     * Control for sorting the time & date of the Reminder
     */
    val time: SortRule = SortRule.NONE,

    /**
     * Control for sorting the description text
     */
    val description: SortRule = SortRule.NONE,
)

/**
 * A control for how to sort a property.
 */
enum class SortRule {
    /**
     * Do not do any sorting on this value.
     */
    NONE,

    /**
     * Sort by ascending value.
     */
    ASCENDING,

    /**
     * Sort by descending value.
     */
    DESCENDING
}