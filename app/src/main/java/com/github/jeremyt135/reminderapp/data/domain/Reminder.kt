package com.github.jeremyt135.reminderapp.data.domain

import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import com.github.jeremyt135.BR
import com.github.jeremyt135.reminderapp.data.DomainModel
import com.github.jeremyt135.reminderapp.data.local.ReminderAndAlarmEntity
import com.github.jeremyt135.reminderapp.data.local.ReminderAndHistoryEntity
import com.github.jeremyt135.reminderapp.data.local.ReminderEntity
import com.github.jeremyt135.reminderapp.util.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
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
            0 -> "No days selected"
            in 1..6 ->
                // Sort for timezones with days of week similarly to the US, with Sunday first
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
            type = this.schedulingRules.type,
            startDate = this.schedulingRules.startDate,
            endDate = this.schedulingRules.endDate,
            time = this.schedulingRules.time,
            daysOfWeek = this.schedulingRules.daysOfWeek,
            vibrate = this.vibrate,
            maxSnoozeRepeats = this.maxSnoozeRepeats,
            snoozeDuration = this.snoozeDuration,
            notifSoundUri = this.notifSoundUri,
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
    REPEATING,

    /**
     * The Reminder repeats forever with no end date
     */
    REPEAT_FOREVER
}

/**
 * Data for determining how to schedule a Reminder.
 */
class SchedulingRules(
    val type: SchedulingType = SchedulingType.SINGLE,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = startDate.plusDays(1),
    time: LocalTime = currentLocalTime(),
    val daysOfWeek: SortedSet<DayOfWeek> = sortedSetOf(),
) {
    var time: LocalTime = time.truncatedTo(ChronoUnit.MINUTES)
        private set(value) {
            field = value.truncatedTo(ChronoUnit.MINUTES)
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

    // True if this Reminder runs every day.
    val isEveryDay: Boolean = daysOfWeek.size == ChronoField.DAY_OF_WEEK.range().maximum.toInt()

    /**
     * Determines the next LocalDateTime that a reminder with these rules could have an alarm scheduled.
     * @param currentDateTime The current date and time, to determine the relative offset for scheduling
     * @param allowSameDay Escape hatch to allow or disallowed a repeating reminder that could be scheduled today
     * to actually be scheduled for today, or if it should be scheduled on the next available day.
     * @return The next LocalDateTime to schedule, or null if the reminder is "expired" (outside
     * the allowed end time).
     */
    fun getNextTimeForAlarm(
        currentDateTime: LocalDateTime,
        allowSameDay: Boolean = false
    ): LocalDateTime? {
        return when (type) {
            SchedulingType.SINGLE -> {
                val startDateTime = startDate.atTime(time)

                // Check if in future
                if (startDateTime >= currentDateTime) {
                    startDateTime
                } else null // Was in past
            }
            SchedulingType.REPEATING, SchedulingType.REPEAT_FOREVER -> {
                if (type == SchedulingType.REPEATING &&
                    currentDateTime > endDate.atTime(time).truncatedTo(ChronoUnit.MINUTES)
                ) {
                    null // expired
                } else if (currentDateTime.toLocalDate() < startDate) {
                    // in future
                    val nextDayOfWeek = firstDayOfWeek { it >= startDate.dayOfWeek } // first day of week on or after startDate
                    startDate.atTime(time).with(TemporalAdjusters.nextOrSame(nextDayOfWeek))
                } else {
                    // check if the reminder time is in the past, preventing it from being scheduled today
                    if (currentDateTime.toLocalTime() > time) {
                        val nextDayOfWeek = firstDayOfWeek { it > currentDateTime.dayOfWeek }
                        currentDateTime.toLocalDate()
                            .atTime(time)
                            .with(TemporalAdjusters.next(nextDayOfWeek))
                    } else {
                        // time is in the future (t >= now), so it could be scheduled for today
                        if (allowSameDay) {
                            currentDateTime.toLocalDate()
                                .atTime(time)
                                .with(TemporalAdjusters.nextOrSame(firstDayOfWeek { it >= currentDateTime.dayOfWeek }))
                        } else {
                            currentDateTime.toLocalDate()
                                .atTime(time)
                                .with(TemporalAdjusters.next(firstDayOfWeek { it > currentDateTime.dayOfWeek }))
                        }

                    }
                }
            }
        }
    }

    /**
     * Returns first day of week matching the predicate, falling back to the first
     * selected DayOfWeek if none of the daysOfWeek match the predicate.
     */
    private fun firstDayOfWeek(predicate: (DayOfWeek) -> Boolean) =
        daysOfWeek.firstOrNull { predicate(it) }
            ?: daysOfWeek.first()

    /**
     * Determines if the date falls within the schedule's
     * start and end dates and also has the same day of week.
     * However, SchedulingType.SINGLE only checks date because it never repeats.
     * @param date Date to check
     * @return True if date is within the schedule's start and end date and also the schedule contains dayOfWeek
     */
    fun containsDate(date: LocalDate): Boolean {
        return when (type) {
            SchedulingType.SINGLE -> date == startDate
            SchedulingType.REPEATING -> {
                date in startDate..endDate && date.dayOfWeek in daysOfWeek
            }
            SchedulingType.REPEAT_FOREVER -> startDate <= date && date.dayOfWeek in daysOfWeek
        }
    }

    fun startDateDisplayString() = if (startDate == LocalDate.now()) {
        "Today"
    } else startDate.getShortLocalDateDisplay()

    fun endDateDisplayString() = when (type) {
        SchedulingType.REPEATING -> if (endDate == LocalDate.now()) {
            "Today"
        } else endDate.getShortLocalDateDisplay()
        SchedulingType.REPEAT_FOREVER -> "Forever"
        SchedulingType.SINGLE -> {
            ""
        }
    }

    fun dayIsScheduled(dayString: String) = dayIsScheduled(DayOfWeek.valueOf(dayString))

    fun dayIsScheduled(day: DayOfWeek) = daysOfWeek.contains(day)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SchedulingRules

        if (type != other.type) return false
        if (startDate != other.startDate) return false
        if (endDate != other.endDate) return false
        if (daysOfWeek != other.daysOfWeek) return false
        if (time != other.time) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + startDate.hashCode()
        result = 31 * result + endDate.hashCode()
        result = 31 * result + daysOfWeek.hashCode()
        result = 31 * result + time.hashCode()
        return result
    }

    override fun toString(): String {
        return "SchedulingRules(type=$type, " +
                "startDate=$startDate, " +
                "endDate=$endDate, " +
                "daysOfWeek=$daysOfWeek, " +
                "time=$time)"
    }

    fun copy(
        type: SchedulingType = this.type,
        startDate: LocalDate = this.startDate,
        endDate: LocalDate = this.endDate,
        time: LocalTime = this.time,
        daysOfWeek: SortedSet<DayOfWeek> = this.daysOfWeek
    ) = SchedulingRules(
        type = type,
        startDate = startDate,
        endDate = endDate,
        daysOfWeek = daysOfWeek,
        time = time
    )
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