package com.github.jeremyt135.reminderapp.data.local

import androidx.room.TypeConverter
import java.time.LocalTime

class Converters {
    @TypeConverter
    fun localTimeFromString(value: String): LocalTime {
        return LocalTime.parse(value)
    }

    @TypeConverter
    fun localTimeToString(value: LocalTime): String {
        return value.toString()
    }
}