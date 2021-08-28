package com.github.jeremyt135.reminderapp.screens.reminder.overview

import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import java.time.DayOfWeek

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
    val reminderStatuses: Set<ReminderStatus> = ReminderStatus.values().toSet()
)

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