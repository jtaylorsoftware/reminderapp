package com.github.jeremyt135.reminderapp.data.local

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    foreignKeys = [
    ForeignKey(
        entity = ReminderEntity::class,
        parentColumns = ["id"],
        childColumns = ["reminder_id"]
    )
])
data class Alarm(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,

    @ColumnInfo(name = "reminder_id")
    val reminderId: Long,

    @ColumnInfo(name = "trigger_at")
    val triggerAt: Long,
): Parcelable
