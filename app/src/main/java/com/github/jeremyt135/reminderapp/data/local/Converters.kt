package com.github.jeremyt135.reminderapp.data.local

import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.*

class Converters {
    @TypeConverter
    fun localTimeFromString(value: String): LocalTime {
        return LocalTime.parse(value)
    }

    @TypeConverter
    fun localTimeToString(value: LocalTime): String {
        return value.toString()
    }

    @TypeConverter
    fun daysOfWeekToString(value: Set<DayOfWeek>): String {
        return value.joinToString()
    }

    @TypeConverter
    fun daysOfWeekFromString(value: String): SortedSet<DayOfWeek> {
        if (value.isBlank()) {
            return sortedSetOf()
        }
        val days = """,\s+""".toRegex().split(value)
        return days.map { DayOfWeek.valueOf(it) }.toSortedSet()
    }
}