package com.github.jeremyt135.reminderapp.data.local

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * Parcelable entity for persisting Alarms.
 *
 * Do not use in putExtra with Intents wrapped in PendingIntent.getBroadcast because
 * it apparently does not work on Android N and later :)
 * https://stackoverflow.com/questions/39478422/pendingintent-getbroadcast-lost-parcelable-data
 */
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
