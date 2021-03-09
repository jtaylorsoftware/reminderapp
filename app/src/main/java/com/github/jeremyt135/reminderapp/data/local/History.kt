package com.github.jeremyt135.reminderapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.github.jeremyt135.reminderapp.data.DataModel
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.util.uuid

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "history_id")
    val id: String = uuid(),

    @ColumnInfo(name = "reminder_id")
    val reminderId: String = "",

    @ColumnInfo(name = "reminder_description")
    val description: String = "",

    @ColumnInfo(name = "completion_status")
    val completionStatus: String = ReminderStatus.COMPLETED.name,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long = 0,
) : DataModel<History> {
    override fun asDomainModel(): History =
        History(
            id = this.id,
            reminderId = this.reminderId,
            description = this.description,
            completionStatus = enumValueOf(this.completionStatus),
            completedAt = this.completedAt,
        )
}