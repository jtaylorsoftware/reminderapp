package com.github.jeremyt135.reminderapp.data.domain

import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import com.github.jeremyt135.BR
import com.github.jeremyt135.reminderapp.data.local.Alarm
import com.github.jeremyt135.reminderapp.data.local.ReminderAndAlarmEntity
import com.github.jeremyt135.reminderapp.data.local.ReminderEntity
import com.github.jeremyt135.reminderapp.util.getFullLocaleDisplay
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.*

enum class ReminderStatus {
    COMPLETED, SKIPPED, PENDING
}

/**
 * Domain class for Reminders that are created and displayed in the application
 */
class Reminder(
    description: String = "",
    var status: ReminderStatus = ReminderStatus.PENDING,
    time: LocalTime = LocalTime.now(),
    val daysOfWeek: SortedSet<DayOfWeek> = sortedSetOf(),
    vibrate: Boolean = false,
    var id: Long = 0L,
) : BaseObservable() {
    var description: String = description
        @Bindable get() = field
        set(value) {
            if (field != value) {
                field = value
                notifyPropertyChanged(BR.reminder)
            }
        }

    var vibrate: Boolean = vibrate
        @Bindable get() = field
        set(value) {
            if (field != value) {
                field = value
                notifyPropertyChanged(BR.reminder)
            }
        }

    var hour: Int = time.hour
        set(value) {
            if (field != value) {
                field = value
                notifyPropertyChanged(BR.reminder)
            }
        }

    var minute: Int = time.minute
        set(value) {
            if (field != value) {
                field = value
            }
        }

    val isComplete: Boolean
        get() = status != ReminderStatus.PENDING

    // True if this Reminder was scheduled for the same day as it was created.
    val isOneOff: Boolean = daysOfWeek.isEmpty()

    // True if this Reminder runs every day.
    val isEveryDay: Boolean = daysOfWeek.size == 7

    /**
     * Returns the next LocalDateTime that this Reminder could have an alarm scheduled,
     * or null if the Reminder is complete or in the past and cannot be scheduled.
     */
    fun getNextTimeForAlarm(today: DayOfWeek): LocalDateTime? {
        if (isComplete) {
            return null
        }

        val nextDayOfWeek = daysOfWeek.firstOrNull { it >= today } ?: LocalDate.now().dayOfWeek

        val reminderTimeToday =
            LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minute))
        val timeIsInPast =
            LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).isAfter(reminderTimeToday)
        return if (isOneOff) {
            if (timeIsInPast) {
                Timber.d("One-off Alarm for today was in the past")
                null
            } else {
                reminderTimeToday
            }
        } else {
            if (timeIsInPast) {
                reminderTimeToday.with(TemporalAdjusters.next(nextDayOfWeek))
            } else {
                reminderTimeToday.with(TemporalAdjusters.nextOrSame(nextDayOfWeek))
            }
        }

    }

    fun alarmCanBeSetToday(today: DayOfWeek): Boolean =
        !isComplete && (this.isOneOff || daysOfWeek.contains(today))

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

    fun getTimeDisplayString() = LocalDateTime.now().withHour(hour).withMinute(minute).format(
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    )

    fun dayIsChecked(dayString: String) = dayIsChecked(DayOfWeek.valueOf(dayString))

    fun dayIsChecked(day: DayOfWeek) = daysOfWeek.contains(day)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Reminder

        if (status != other.status) return false
        if (daysOfWeek != other.daysOfWeek) return false
        if (id != other.id) return false
        if (description != other.description) return false
        if (vibrate != other.vibrate) return false
        if (hour != other.hour) return false
        if (minute != other.minute) return false

        return true
    }

    override fun hashCode(): Int {
        var result = status.hashCode()
        result = 31 * result + daysOfWeek.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + vibrate.hashCode()
        result = 31 * result + hour
        result = 31 * result + minute
        return result
    }

    override fun toString(): String {
        return "Reminder(" +
                "status=$status, " +
                "daysOfWeek=$daysOfWeek, " +
                "id=$id, " +
                "description='$description', " +
                "vibrate=$vibrate, " +
                "hour=$hour, " +
                "minute=$minute" +
                ")"
    }


    fun copy(
        description: String = this.description,
        status: ReminderStatus = this.status,
        time: LocalTime = LocalTime.of(this.hour, this.minute),
        daysOfWeek: SortedSet<DayOfWeek> = this.daysOfWeek.toSortedSet(),
        vibrate: Boolean = this.vibrate,
        id: Long = this.id,
    ) = Reminder(description, status, time, daysOfWeek, vibrate, id)
}

fun Reminder.asEntity(): ReminderEntity =
    ReminderEntity(
        id = this.id,
        description = this.description,
        time = LocalTime.of(hour, minute),
        daysOfWeek = this.daysOfWeek.toSortedSet(),
        status = this.status.name,
        vibrate = this.vibrate
    )

fun List<Reminder>.asEntity(): List<ReminderEntity> = map {
    it.asEntity()
}

data class ReminderAndAlarm(val reminder: Reminder, val alarm: Alarm)

fun ReminderAndAlarm.asEntity(): ReminderAndAlarmEntity =
    ReminderAndAlarmEntity(
        reminder = this.reminder.asEntity(),
        alarm = this.alarm
    )