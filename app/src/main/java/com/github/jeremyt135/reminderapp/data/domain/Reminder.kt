package com.github.jeremyt135.reminderapp.data.domain

import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import com.github.jeremyt135.BR
import com.github.jeremyt135.reminderapp.data.DomainModel
import com.github.jeremyt135.reminderapp.data.local.ReminderAndAlarmEntity
import com.github.jeremyt135.reminderapp.data.local.ReminderAndHistoryEntity
import com.github.jeremyt135.reminderapp.data.local.ReminderEntity
import com.github.jeremyt135.reminderapp.util.*
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAdjusters
import java.util.*

/**
 * Domain class for Reminders. Exposes some data through bound properties
 * for displaying data in layouts.
 */
class Reminder(
    val id: String = uuid(),
    description: String = "",
    vibrate: Boolean = false,
    var notifSoundUri: String = "",
    snoozeRules: SnoozeRules = SnoozeRules(),
    schedulingRules: SchedulingRules = SchedulingRules(),
) : BaseObservable(), DomainModel<ReminderEntity> {
    var snoozeRules = snoozeRules
        private set

    var schedulingRules = schedulingRules
        private set

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

    @get:Bindable
    var maxSnoozeRepeats: Long
        get() = snoozeRules.maxRepeats
        set(value) {
            if (snoozeRules.maxRepeats != value) {
                snoozeRules = snoozeRules.copy(maxRepeats = value)
                notifyPropertyChanged(BR.maxSnoozeRepeats)
            }
        }

    @get:Bindable
    var snoozeDuration: Long
        get() = snoozeRules.duration
        set(value) {
            if (snoozeRules.duration != value) {
                snoozeRules = snoozeRules.copy(duration = value)
                notifyPropertyChanged(BR.snoozeDuration)
            }
        }

    fun getDaysDisplayString() =
        when (schedulingRules.daysOfWeek.size) {
            // TODO: Actually return Today's date?
            0 -> "Today"
            in 1..6 ->
                // Sort for timezones with days of week similarly to the US, with Sunday first
                // TODO: do sort correctly for any local timezone (would need to also display the date toggles similarly)
                ("Every " + schedulingRules.daysOfWeek.sortedBy {

                    // Convert from 1..7 to 0..6
                    it.value % it.range(ChronoField.DAY_OF_WEEK).maximum
                }.joinToString { it.getFullLocaleDisplay() })
            7 -> "Every day"
            else -> throw IllegalArgumentException("daysOfWeek has more than 7 entries")
        }

    fun getTimeDisplayString(): String =
        LocalDateTime.now().withHour(schedulingRules.hour).withMinute(schedulingRules.minute)
            .getShortLocalTimeDisplay()


    fun copy(
        id: String = this.id,
        description: String = this.description,
        vibrate: Boolean = this.vibrate,
        notifSoundUri: String = this.notifSoundUri,
        snoozeRules: SnoozeRules = this.snoozeRules,
        schedulingRules: SchedulingRules = this.schedulingRules,
    ) = Reminder(
        id = id,
        description = description,
        notifSoundUri = notifSoundUri,
        vibrate = vibrate,
        snoozeRules = snoozeRules,
        schedulingRules = schedulingRules,
    )

    override fun asDataModel(): ReminderEntity =
        ReminderEntity(
            id = this.id,
            description = this.description,
            vibrate = this.vibrate,
            notifSoundUri = this.notifSoundUri,
            type = this.schedulingRules.type,
            startDate = this.schedulingRules.startDate,
            endDate = this.schedulingRules.endDate,
            time = this.schedulingRules.time,

            maxSnoozeRepeats = this.maxSnoozeRepeats,
            snoozeDuration = this.snoozeDuration,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Reminder

        if (id != other.id) return false
        if (description != other.description) return false
        if (vibrate != other.vibrate) return false
        if (schedulingRules != other.schedulingRules) return false
        if (snoozeRules != other.snoozeRules) return false
        if (notifSoundUri != other.notifSoundUri) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + vibrate.hashCode()
        result = 31 * result + schedulingRules.hashCode()
        result = 31 * result + snoozeRules.hashCode()
        result = 31 * result + notifSoundUri.hashCode()
        return result
    }

    override fun toString(): String {
        return "Reminder(" +
                "id=$id, " +
                "description='$description', " +
                "vibrate=$vibrate, " +
                "schedulingRules=$schedulingRules, " +
                "snoozeRules=$snoozeRules, " +
                "notifSoundUri=$notifSoundUri, " +
                ")"
    }

}

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
 * Types of Reminders as it relates to scheduling
 */
enum class SchedulingType {
    /**
     * The Reminder is set for one specific date
     */
    SINGLE,

    /**
     * The Reminder repeats on multiple days over a span of time
     */
    REPEATING
}

/**
 * Data for determining how to schedule a Reminder.
 */
data class SchedulingRules(
    val type: SchedulingType = SchedulingType.SINGLE,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now(),
    val time: LocalTime = currentLocalTime(),
    val daysOfWeek: SortedSet<DayOfWeek> = sortedSetOf(),
    val repeatsTimes: Long = 0,
) {

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

    fun dayIsScheduled(dayString: String) = dayIsScheduled(DayOfWeek.valueOf(dayString))

    fun dayIsScheduled(day: DayOfWeek) = daysOfWeek.contains(day)
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