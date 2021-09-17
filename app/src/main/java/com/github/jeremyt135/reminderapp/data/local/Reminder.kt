package com.github.jeremyt135.reminderapp.data.local

import androidx.room.*
import com.github.jeremyt135.reminderapp.data.DataModel
import com.github.jeremyt135.reminderapp.data.domain.*
import com.github.jeremyt135.reminderapp.util.uuid
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey
    val id: String = uuid(),

    val description: String = "",

    val type: SchedulingType = SchedulingType.SINGLE,

    @ColumnInfo(name = "start_date")
    val startDate: LocalDate = LocalDate.now(),

    @ColumnInfo(name = "end_date")
    val endDate: LocalDate = LocalDate.now(),

    val time: LocalTime = LocalTime.now(),

    @ColumnInfo(name = "days_of_week")
    val daysOfWeek: SortedSet<DayOfWeek> = sortedSetOf(),

    val vibrate: Boolean = false,

    @ColumnInfo(name = "max_snooze_repeats")
    val maxSnoozeRepeats: Long = SnoozeRules.DEFAULT_SNOOZE_REPEATS,

    @ColumnInfo(name = "snooze_time")
    val snoozeDuration: Long = SnoozeRules.DEFAULT_SNOOZE_DURATION,

    @ColumnInfo(name = "notif_sound_uri")
    val notifSoundUri: String = "",
) : DataModel<Reminder> {
    override fun asDomainModel(): Reminder =
        Reminder(
            id = this.id,
            description = this.description,
            vibrate = this.vibrate,
            notifSoundUri = this.notifSoundUri,
            snoozeRules = SnoozeRules(maxRepeats = maxSnoozeRepeats, duration = snoozeDuration),
            schedulingRules = SchedulingRules(
                type = type,
                startDate = startDate,
                endDate = endDate,
                time = time,
                daysOfWeek = daysOfWeek,
            )
        )
}

data class ReminderAndAlarmEntity(
    @Embedded val reminder: ReminderEntity,
    @Embedded val alarm: AlarmEntity?
) : DataModel<ReminderAndAlarm> {
    override fun asDomainModel(): ReminderAndAlarm =
        ReminderAndAlarm(
            reminder = this.reminder.asDomainModel(),
            alarm = this.alarm?.asDomainModel()
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