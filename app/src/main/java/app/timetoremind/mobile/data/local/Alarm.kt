package app.timetoremind.mobile.data.local

import android.os.Parcelable
import androidx.room.*
import app.timetoremind.mobile.data.DataModel
import app.timetoremind.mobile.data.domain.Alarm
import app.timetoremind.mobile.data.domain.SnoozeRules
import app.timetoremind.mobile.util.NotificationId
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
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["reminder_id"])
    ]
)
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "alarm_id")
    var id: Long = 0L,

    @ColumnInfo(name = "reminder_id")
    val reminderId: String = "",

    @ColumnInfo(name = "trigger_at")
    val triggerAt: LocalDateTime,

    val scheduled: Boolean? = null,

    @ColumnInfo(name = "notification_id")
    val notificationId: Int = NotificationId.next,

    @ColumnInfo(name = "alarm_max_snooze_repeats")
    val maxSnoozeRepeats: Long = SnoozeRules.DEFAULT_SNOOZE_REPEATS,

    @ColumnInfo(name = "alarm_snooze_time")
    val snoozeDuration: Long = SnoozeRules.DEFAULT_SNOOZE_DURATION,

    @ColumnInfo(name = "snooze_count")
    val snoozeCount: Long = 0
) : Parcelable, DataModel<Alarm> {
    override fun asDomainModel(): Alarm = Alarm(
        id = this.id,
        reminderId = this.reminderId,
        triggerAt = this.triggerAt,
        scheduled = this.scheduled,
        notificationId = this.notificationId,
        maxSnoozeRepeats = this.maxSnoozeRepeats,
        snoozeDuration = this.snoozeDuration,
        snoozeCount = this.snoozeCount
    )
}
