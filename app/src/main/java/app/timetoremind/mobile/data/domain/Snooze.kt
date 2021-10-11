package app.timetoremind.mobile.data.domain

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * Data for determining how an alarm can snooze.
 */
@Parcelize
data class SnoozeRules(
    /**
     * The maximum times an alarm can snooze.
     * A value of 0 indicates no snooze allowed,
     * a value of Long.MAX_VALUE indicates infinite snoozes allowed.
     */
    val maxRepeats: Long = DEFAULT_SNOOZE_REPEATS,
    /**
     * How long to snooze each time, in minutes.
     */
    val duration: Long = DEFAULT_SNOOZE_DURATION
) : Parcelable {
    @IgnoredOnParcel
    val neverRepeats = maxRepeats == 0L

    @IgnoredOnParcel
    val alwaysRepeats = maxRepeats == ALWAYS_REPEATS

    init {
        // Ensure a sane bounds on snoozeDuration
        if (duration < MIN_SNOOZE_DURATION || duration > MAX_SNOOZE_DURATION) {
            throw IllegalStateException("Invalid duration $duration")
        }
        if (maxRepeats < 0) {
            throw IllegalStateException("Invalid maxRepeats $maxRepeats")
        }
    }

    /**
     * Determines if an alarm could still snooze according to these rules and the number
     * of snooze attempts.
     * @return True if these rules allow repeated snoozes and if the max is not exceeded.
     */
    fun canSnooze(currentRepeats: Long): Boolean =
        alwaysRepeats || !neverRepeats && currentRepeats < maxRepeats

    /**
     * Returns a string for displaying a description of the snooze rules in the UI.
     */
    fun getDisplayString(): String = when (maxRepeats) {
        0L -> "Never snooze"
        ALWAYS_REPEATS -> "Snooze for $duration minutes, forever"
        else -> "Snooze for $duration minutes, $maxRepeats times"
    }

    companion object {
        private const val ALWAYS_REPEATS = Long.MAX_VALUE
        const val MAX_SNOOZE_DURATION = 60L
        const val MIN_SNOOZE_DURATION = 1L
        const val DEFAULT_SNOOZE_DURATION = 5L
        const val DEFAULT_SNOOZE_REPEATS = ALWAYS_REPEATS
    }
}
