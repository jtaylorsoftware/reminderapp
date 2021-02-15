package com.github.jeremyt135.reminderapp.data.local

import androidx.room.*
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
    val vibrate: Boolean = false,
    @ColumnInfo(name = "completion_status")
    val completionStatus: String = ReminderStatus.PENDING.name
)

fun ReminderEntity.asDomainModel(): Reminder =
    Reminder(
        id = this.id,
        description = this.description,
        time = this.time,
        daysOfWeek = this.daysOfWeek.toSortedSet(),
        status = enumValueOf(this.status),
        vibrate = this.vibrate,
        completionStatus = enumValueOf(this.completionStatus)
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