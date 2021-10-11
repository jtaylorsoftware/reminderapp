package app.timetoremind.mobile.data.local

import androidx.room.TypeConverter
import app.timetoremind.mobile.data.domain.SchedulingType
import app.timetoremind.mobile.util.toEpochMilli
import app.timetoremind.mobile.util.toLocalDateTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

class Converters {
    @TypeConverter
    fun localTimeFromString(value: String): LocalTime = LocalTime.parse(value)

    @TypeConverter
    fun localTimeToString(value: LocalTime): String = value.toString()

    @TypeConverter
    fun localDateToString(value: LocalDate): String = value.toString()

    @TypeConverter
    fun localDateFromString(value: String): LocalDate = LocalDate.parse(value)

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

    @TypeConverter
    fun schedulingTypeToString(value: SchedulingType) = value.toString()

    @TypeConverter
    fun schedulingTypeFromString(value: String) = SchedulingType.valueOf(value)
}