package com.github.jeremyt135.reminderapp.data.domain

/**
 * Data for determining how an alarm can snooze.
 */
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
) {
    val neverRepeats = maxRepeats == 0L
    val alwaysRepeats = maxRepeats == Long.MAX_VALUE

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

    companion object {
        const val MAX_SNOOZE_DURATION = 30L
        const val MIN_SNOOZE_DURATION = 1L
        const val DEFAULT_SNOOZE_DURATION = 5L
        const val DEFAULT_SNOOZE_REPEATS = 3L
    }
}

/**
 * Options for how many times an alarm can snooze
 */
enum class SnoozeRepetitionOption {
    None, Infinite, Choose
}