package com.github.jeremyt135.reminderapp.data.domain

import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import com.github.jeremyt135.BR
import com.github.jeremyt135.reminderapp.data.local.ReminderEntity
import java.time.LocalTime

enum class ReminderStatus {
    COMPLETED, SKIPPED, PENDING
}

/**
 * Domain class for Reminders that are created and displayed in the application
 */
class Reminder(
    description: String,
    var status: ReminderStatus,
    time: LocalTime = LocalTime.now(),
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Reminder

        if (status != other.status) return false
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
        result = 31 * result + id.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + canSkip.hashCode()
        result = 31 * result + vibrate.hashCode()
        result = 31 * result + hour
        result = 31 * result + minute
        return result
    }

    override fun toString(): String {
        return "Reminder(status=$status, id=$id, description='$description', canSkip=$canSkip, vibrate=$vibrate, time=${
            LocalTime.of(
                hour,
                minute
            )
        })"
    }

}

fun Reminder.asEntity(): ReminderEntity =
    ReminderEntity(
        id = this.id,
        description = this.description,
        time = LocalTime.of(hour, minute),
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
 */
fun emptyReminder() = Reminder("", ReminderStatus.PENDING)