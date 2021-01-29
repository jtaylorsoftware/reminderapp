package com.github.jeremyt135.reminderapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.github.jeremyt135.reminderapp.data.domain.Reminder

@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val description: String,
    val status: String
)

fun ReminderEntity.asDomainModel(): Reminder =
    Reminder(id = this.id, description = this.description, status = enumValueOf(this.status))

fun List<ReminderEntity>.asDomainModel(): List<Reminder> = map {
    it.asDomainModel()
}