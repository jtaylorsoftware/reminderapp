package com.github.jeremyt135.reminderapp.data.domain

import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import com.github.jeremyt135.BR
import com.github.jeremyt135.reminderapp.data.local.ReminderEntity
import com.github.jeremyt135.reminderapp.util.getFullLocaleDisplay
import java.lang.IllegalArgumentException
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.temporal.ChronoField

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
    val daysOfWeek: MutableSet<DayOfWeek> = mutableSetOf(),
    canSkip: Boolean = false,
    vibrate: Boolean = false,
    val id: Long = 0L
) : BaseObservable() {
    var description: String = description
        @Bindable get() = field
        set(value) {
            if (field != value) {
                field = value
                notifyPropertyChanged(BR.reminder)
            }
        }

    var canSkip: Boolean = canSkip
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

    val datesDisplay: String
        get() =
            when (daysOfWeek.size) {
                // TODO: Actually return Today's date?, disallow schedules that don't repeat?
                0 -> "Today"
                in 1..6 ->
                    ("Every " + daysOfWeek.sortedBy {
                        // As per docs for DayOfWeek, MONDAY=1, SUNDAY=7, range=(1..7)
                        // So this puts Sunday first, Saturday last
                        it.value % it.range(ChronoField.DAY_OF_WEEK).maximum
                    }.joinToString { it.getFullLocaleDisplay() })
                7 -> "Every day"
                else -> throw IllegalArgumentException("daysOfWeek has more than 7 entries")
            }


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
        if (canSkip != other.canSkip) return false
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
        result = 31 * result + canSkip.hashCode()
        result = 31 * result + vibrate.hashCode()
        result = 31 * result + hour
        result = 31 * result + minute
        return result
    }

    override fun toString(): String {
        return "Reminder(" +
                "id=$id, " +
                "description='$description', " +
                "status=$status, " +
                "canSkip=$canSkip, " +
                "vibrate=$vibrate, " +
                "daysOfWeek=$daysOfWeek, " +
                "hour=$hour, " +
                "minute=$minute" +
                ")"
    }


}

fun Reminder.asEntity(): ReminderEntity =
    ReminderEntity(
        id = this.id,
        description = this.description,
        time = LocalTime.of(hour, minute),
        daysOfWeek = this.daysOfWeek.toSet(),
        status = this.status.toString(),
        canSkip = this.canSkip,
        vibrate = this.vibrate
    )

fun List<Reminder>.asEntity(): List<ReminderEntity> = map {
    it.asEntity()
}

/**
 * Returns a Reminder with String properties initialized to the empty string "" and ReminderStatus
 * set to PENDING.
 * TODO: Factor out, Reminder now has all default arguments
 */
fun emptyReminder() = Reminder()