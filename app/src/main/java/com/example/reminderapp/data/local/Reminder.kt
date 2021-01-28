package com.example.reminderapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.reminderapp.data.domain.Reminder

@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val description: String
)

fun ReminderEntity.asDomainModel(): Reminder =
    Reminder(id = this.id, description = this.description)

fun List<ReminderEntity>.asDomainModel(): List<Reminder> = map {
    it.asDomainModel()
}