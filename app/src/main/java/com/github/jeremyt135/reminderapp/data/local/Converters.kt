package com.github.jeremyt135.reminderapp.data.local

import androidx.room.TypeConverter
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import com.github.jeremyt135.reminderapp.util.toLocalDateTime
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

class Converters {
    @TypeConverter
    fun localTimeFromString(value: String): LocalTime = LocalTime.parse(value)

    @TypeConverter
    fun localTimeToString(value: LocalTime): String = value.toString()

    @TypeConverter
    fun daysOfWeekToString(value: Set<DayOfWeek>): String = value.joinToString()

    @TypeConverter
    fun localDateTimeFromEpochMilli(value: Long): LocalDateTime = value.toLocalDateTime()

    @TypeConverter
    fun localDateTimeToEpochMilli(value: LocalDateTime) = value.toEpochMilli()

    @TypeConverter
    fun daysOfWeekFromString(value: String): SortedSet<DayOfWeek> {
        if (value.isBlank()) {
            return sortedSetOf()
        }
        val days = """,\s+""".toRegex().split(value)
        return days.map { DayOfWeek.valueOf(it) }.toSortedSet()
    }
}