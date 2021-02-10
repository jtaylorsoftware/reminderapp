package com.github.jeremyt135.reminderapp.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
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
    val vibrate: Boolean = false
)

fun ReminderEntity.asDomainModel(): Reminder =
    Reminder(
        id = this.id,
        description = this.description,
        time = this.time,
        daysOfWeek = this.daysOfWeek.toSortedSet(),
        status = enumValueOf(this.status),
        vibrate = this.vibrate
    )

fun List<ReminderEntity>.asDomainModel(): List<Reminder> = map {
    it.asDomainModel()
}

data class ReminderAndAlarmEntity(
    @Embedded val reminder: ReminderEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "reminder_id"
    )
    val alarm: Alarm
)

fun ReminderAndAlarmEntity.asDomainModel(): ReminderAndAlarm =
    ReminderAndAlarm(
        reminder = this.reminder.asDomainModel(),
        alarm = this.alarm
    )