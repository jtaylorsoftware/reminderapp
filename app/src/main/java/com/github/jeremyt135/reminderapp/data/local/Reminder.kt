package com.github.jeremyt135.reminderapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.*

@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val description: String = "",
    val time: LocalTime = LocalTime.now(),
    val daysOfWeek: SortedSet<DayOfWeek> = sortedSetOf(),
    val status: String = ReminderStatus.PENDING.name,
    val canSkip: Boolean = false,
    val vibrate: Boolean = false
)

fun ReminderEntity.asDomainModel(): Reminder =
    Reminder(
        id = this.id,
        description = this.description,
        time = this.time,
        daysOfWeek = this.daysOfWeek.toSortedSet(),
        status = enumValueOf(this.status),
        canSkip = this.canSkip,
        vibrate = this.vibrate
    )

fun List<ReminderEntity>.asDomainModel(): List<Reminder> = map {
    it.asDomainModel()
}