package com.github.jeremyt135.reminderapp.data.domain

/**
 * Possible snooze interval values.
 */
enum class SnoozeInterval(val value: Int) {
    ONCE(1), THREE(3), FIVE(5), FOREVER(0), NEVER(-1);

    override fun toString(): String = when (this) {
        NEVER -> "Never"
        FOREVER -> "Forever"
        else -> "$value time${if (value > 1) "s" else ""}"
    }

    fun canStillRepeat(snoozeCount: Int): Boolean {
        require(snoozeCount >= 0) {
            "snoozeCount must be a positive value"
        }
        return this == FOREVER || value >= 1 && snoozeCount < value
    }

    fun remainingSnoozes(snoozeCount: Int): Int {
        require(snoozeCount >= 0) {
            "snoozeCount must be a positive value"
        }
        return when (this) {
            NEVER -> 0
            FOREVER -> Int.MAX_VALUE
            else -> (value - snoozeCount).coerceAtLeast(0)
        }
    }

    companion object {
        private val map: Map<Int, SnoozeInterval> by lazy {
            values().associateBy(SnoozeInterval::value)
        }

        fun fromInt(value: Int) =
            map[value] ?: throw IllegalArgumentException("Invalid SnoozeInterval value")

        fun fromString(string: String): SnoozeInterval = when (string) {
            "Never" -> NEVER
            "Forever" -> FOREVER
            else -> {
                map[Character.getNumericValue(string.first())]
                    ?: throw IllegalArgumentException("Invalid SnoozeInterval string")
            }
        }
    }
}

val SnoozeInterval.neverRepeats: Boolean
    get() = this == SnoozeInterval.NEVER

fun SnoozeInterval.canNotRepeat(snoozeCount: Int) = !canStillRepeat(snoozeCount)

/**
 * Possible snooze duration values in minutes.
 */
enum class SnoozeDuration(val value: Long) {
    FIVE(5), TEN(10), FIFTEEN(15), THIRTY(30);

    override fun toString(): String = "$value minutes"

    companion object {
        private val map: Map<Long, SnoozeDuration> by lazy {
            values().associateBy(SnoozeDuration::value)
        }

        fun fromLong(value: Long) =
            map[value] ?: throw IllegalArgumentException("Invalid SnoozeDuration value")

        fun fromString(string: String): SnoozeDuration =
            map[string.substring(0..1).trim().toLong()]
                ?: throw IllegalArgumentException("Invalid SnoozeDuration string")
    }
}

