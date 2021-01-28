package com.example.reminderapp.data.domain

import com.example.reminderapp.data.local.ReminderEntity

data class Reminder(val description: String, val id: Long = 0L)

fun Reminder.asEntity(): ReminderEntity = ReminderEntity(this.id, this.description)

fun List<Reminder>.asEntity(): List<ReminderEntity> = map {
    it.asEntity()
}