package com.github.jeremyt135.reminderapp.data.domain

import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import com.github.jeremyt135.BR
import com.github.jeremyt135.reminderapp.data.DomainModel
import com.github.jeremyt135.reminderapp.data.local.ReminderAndAlarmEntity
import com.github.jeremyt135.reminderapp.data.local.ReminderAndHistoryEntity
import com.github.jeremyt135.reminderapp.data.local.ReminderEntity
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.github.jeremyt135.reminderapp.util.getFullLocaleDisplay
import com.github.jeremyt135.reminderapp.util.getShortLocalTimeDisplay
import com.github.jeremyt135.reminderapp.util.uuid
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAdjusters
import java.util.*

/**
 * Completion status of a Reminder.
 */
enum class ReminderStatus {
    /**
     * The Reminder is completed for today.
     */
    COMPLETED,

    /**
     * The Reminder is skipped for today.
     */
    SKIPPED,

    /**
     * The Reminder is scheduled for an alarm for today that has not yet gone off.
     */
    PENDING
}

/**
 * Domain class for Reminders that are created and displayed in the application
 */
class Reminder(
    description: String = "",
    time: LocalTime = LocalTime.now(),
    val daysOfWeek: SortedSet<DayOfWeek> = sortedSetOf(),
    vibrate: Boolean = false,
    var id: String = uuid(),
    var snoozeInterval: SnoozeInterval = SnoozeInterval.FOREVER,
    var snoozeDuration: SnoozeDuration = SnoozeDuration.FIVE,
    var notifSoundUri: String = "",
) : BaseObservable(), DomainModel<ReminderEntity> {
    @get:Bindable
    var description: String = description
        set(value) {
            if (field != value) {
                field = value
                notifyPropertyChanged(BR.description)
            }
        }

    @get:Bindable
    var vibrate: Boolean = vibrate
        set(value) {
            if (field != value) {
                field = value
                notifyPropertyChanged(BR.vibrate)
            }
        }

    var hour: Int = time.hour
        set(value) {
            if (field != value) {
                field = value
            }
        }

    var minute: Int = time.minute
        set(value) {
            if (field != value) {
                field = value
            }
        }

    // True if this Reminder was scheduled for the same day as it was created and does not repeat.
    val isOneOff: Boolean = daysOfWeek.isEmpty()

    // True if this Reminder runs every day.
    val isEveryDay: Boolean = daysOfWeek.size == ChronoField.DAY_OF_WEEK.range().maximum.toInt()

    /**
     * Returns the next LocalDateTime that this Reminder could have an alarm scheduled,
     * or null if the Reminder is a one-off in the past and cannot be scheduled.
     */
    fun getNextTimeForAlarm(today: DayOfWeek, allowSameDay: Boolean = true): LocalDateTime? {
        val reminderTimeToday =
            LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minute))
        val timeIsInPast = currentLocalDateTime().isAfter(reminderTimeToday)

        return if (isOneOff) {
            if (timeIsInPast) {
                Timber.d("One-off Alarm for today was in the past")
                null
            } else {
                reminderTimeToday
            }
        } else {
            val nextDayOfWeek = daysOfWeek.firstOrNull { it > today } ?: daysOfWeek.first()
            if (timeIsInPast) {
                Timber.d("Repeating alarm (in past) next day of week -> $nextDayOfWeek")
                reminderTimeToday.with(TemporalAdjusters.next(nextDayOfWeek))
            } else {
                val nextDayOfWeek = if (daysOfWeek.contains(today) && allowSameDay) {
                    today
                } else {
                    nextDayOfWeek
                }
                Timber.d("Repeating alarm (time >= now) next day of week -> $nextDayOfWeek")
                reminderTimeToday.with(
                    if (allowSameDay) {
                        TemporalAdjusters.nextOrSame(nextDayOfWeek)
                    } else {
                        TemporalAdjusters.next(nextDayOfWeek)
                    }
                )
            }
        }
    }

    fun getDaysDisplayString() =
        when (daysOfWeek.size) {
            // TODO: Actually return Today's date?
            0 -> "Today"
            in 1..6 ->
                // Sort for timezones with days of week similarly to the US, with Sunday first
                // TODO: do sort correctly for any local timezone (would need to also display the date toggles similarly)
                ("Every " + daysOfWeek.sortedBy {

                    // Convert from 1..7 to 0..6
                    it.value % it.range(ChronoField.DAY_OF_WEEK).maximum
                }.joinToString { it.getFullLocaleDisplay() })
            7 -> "Every day"
            else -> throw IllegalArgumentException("daysOfWeek has more than 7 entries")
        }

    fun getTimeDisplayString(): String =
        LocalDateTime.now().withHour(hour).withMinute(minute).getShortLocalTimeDisplay()

    fun dayIsChecked(dayString: String) = dayIsChecked(DayOfWeek.valueOf(dayString))

    fun dayIsChecked(day: DayOfWeek) = daysOfWeek.contains(day)

    fun copy(
        description: String = this.description,
        time: LocalTime = LocalTime.of(this.hour, this.minute),
        daysOfWeek: SortedSet<DayOfWeek> = this.daysOfWeek.toSortedSet(),
        vibrate: Boolean = this.vibrate,
        id: String = this.id,
        snoozeInterval: SnoozeInterval = this.snoozeInterval,
        snoozeDuration: SnoozeDuration = this.snoozeDuration,
        notifSoundUri: String = this.notifSoundUri
    ) = Reminder(
        description,
        time,
        daysOfWeek,
        vibrate,
        id,
        snoozeInterval,
        snoozeDuration,
        notifSoundUri
    )

    override fun asDataModel(): ReminderEntity =
        ReminderEntity(
            id = this.id,
            description = this.description,
            time = LocalTime.of(hour, minute),
            daysOfWeek = this.daysOfWeek.toSortedSet(),
            vibrate = this.vibrate,
            snoozeInterval = this.snoozeInterval,
            snoozeDuration = this.snoozeDuration,
            notifSoundUri = this.notifSoundUri
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Reminder

        if (daysOfWeek != other.daysOfWeek) return false
        if (id != other.id) return false
        if (description != other.description) return false
        if (vibrate != other.vibrate) return false
        if (hour != other.hour) return false
        if (minute != other.minute) return false
        if (snoozeInterval != other.snoozeInterval) return false
        if (snoozeDuration != other.snoozeDuration) return false
        if (notifSoundUri != other.notifSoundUri) return false

        return true
    }

    override fun hashCode(): Int {
        var result = daysOfWeek.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + vibrate.hashCode()
        result = 31 * result + hour
        result = 31 * result + minute
        result = 31 * result + snoozeInterval.hashCode()
        result = 31 * result + snoozeDuration.hashCode()
        result = 31 * result + notifSoundUri.hashCode()
        return result
    }

    override fun toString(): String {
        return "Reminder(" +
                "daysOfWeek=$daysOfWeek, " +
                "id=$id, " +
                "description='$description', " +
                "vibrate=$vibrate, " +
                "hour=$hour, " +
                "minute=$minute, " +
                "snoozeInterval=$snoozeInterval, " +
                "snoozeDuration=$snoozeDuration, " +
                "notifSoundUri=$notifSoundUri, " +
                ")"
    }

}

data class ReminderAndAlarm(val reminder: Reminder, val alarm: Alarm?) :
    DomainModel<ReminderAndAlarmEntity> {
    override fun asDataModel() = ReminderAndAlarmEntity(
        reminder = this.reminder.asDataModel(),
        alarm = this.alarm?.asDataModel()
    )
}

data class ReminderAndHistory(val reminder: Reminder, val history: History?) : BaseObservable(),
    DomainModel<ReminderAndHistoryEntity> {
    /**
     * The status for this Reminder as it pertains to history and daily completion. For a one-off
     * Reminder, completionStatus should match this.status. Otherwise, the value is set based on
     * today's History for this Reminder.
     */
    @get:Bindable
    var completionStatus: ReminderStatus = history?.completionStatus ?: ReminderStatus.PENDING
        set(value) {
            if (field != value) {
                field = value
                notifyPropertyChanged(BR.completionStatus)
            }
        }

    override fun asDataModel() = ReminderAndHistoryEntity(
        reminder = this.reminder.asDataModel(),
        history = this.history?.asDataModel()
    )
}