package com.github.jeremyt135.reminderapp.data.domain

import com.github.jeremyt135.reminderapp.data.local.ReminderEntity

enum class ReminderStatus {
    COMPLETED, SKIPPED, PENDING
}

/**
 * Domain class for Reminders that are created and displayed in the application
 */
data class Reminder(val description: String, val status: ReminderStatus, val id: Long = 0L)

fun Reminder.asEntity(): ReminderEntity =
    ReminderEntity(id = this.id, description = this.description, status = this.status.toString())

fun List<Reminder>.asEntity(): List<ReminderEntity> = map {
    it.asEntity()
}

/**
 * Returns a Reminder with String properties initialized to the empty string "" and ReminderStatus
 * set to PENDING.
 */
fun emptyReminder() = Reminder("", ReminderStatus.PENDING)