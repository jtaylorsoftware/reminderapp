package com.github.jeremyt135.reminderapp.data.local

import androidx.room.*
import com.github.jeremyt135.reminderapp.data.DataModel
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndAlarm
import com.github.jeremyt135.reminderapp.data.domain.ReminderAndHistory
import com.github.jeremyt135.reminderapp.util.uuid
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.*

@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey
    val id: String = uuid(),
    val description: String = "",
    val time: LocalTime = LocalTime.now(),
    @ColumnInfo(name = "days_of_week")
    val daysOfWeek: SortedSet<DayOfWeek> = sortedSetOf(),
    val vibrate: Boolean = false,
) : DataModel<Reminder> {
    override fun asDomainModel(): Reminder =
        Reminder(
            id = this.id,
            description = this.description,
            time = this.time,
            daysOfWeek = this.daysOfWeek.toSortedSet(),
            vibrate = this.vibrate,
        )
}

data class ReminderAndAlarmEntity(
    @Embedded val reminder: ReminderEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "reminder_id"
    )
    val alarm: AlarmEntity
) : DataModel<ReminderAndAlarm> {
    override fun asDomainModel(): ReminderAndAlarm =
        ReminderAndAlarm(
            reminder = this.reminder.asDomainModel(),
            alarm = this.alarm.asDomainModel()
        )
}

data class ReminderAndHistoryEntity(
    @Embedded val reminder: ReminderEntity,
    @Embedded val history: HistoryEntity?
) : DataModel<ReminderAndHistory> {
    override fun asDomainModel(): ReminderAndHistory =
        ReminderAndHistory(
            reminder = this.reminder.asDomainModel(),
            history = this.history?.asDomainModel()
        )
}