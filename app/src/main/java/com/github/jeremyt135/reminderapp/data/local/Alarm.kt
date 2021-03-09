package com.github.jeremyt135.reminderapp.data.local

import android.os.Parcelable
import androidx.room.*
import com.github.jeremyt135.reminderapp.data.DataModel
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.util.NotificationId
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

/**
 * Parcelable entity for persisting Alarms.
 *
 * Do not use in putExtra with Intents wrapped in PendingIntent.getBroadcast because
 * it apparently does not work on Android N and later :)
 * https://stackoverflow.com/questions/39478422/pendingintent-getbroadcast-lost-parcelable-data
 */
@Parcelize
@Entity(
    tableName = "alarm",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminder_id"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [
        Index(value = ["reminder_id"])
    ]
)
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,

    @ColumnInfo(name = "reminder_id")
    val reminderId: String = "",

    @ColumnInfo(name = "trigger_at")
    val triggerAt: LocalDateTime,

    val scheduled: Boolean? = null,

    @ColumnInfo(name = "notification_id")
    val notificationId: Int = NotificationId.next
) : Parcelable, DataModel<Alarm> {
    override fun asDomainModel(): Alarm = Alarm(
        id = this.id,
        reminderId = this.reminderId,
        triggerAt = this.triggerAt,
        scheduled = this.scheduled,
        notificationId = this.notificationId
    )
}
