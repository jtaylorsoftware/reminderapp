package com.github.jeremyt135.reminderapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "history_id")
    val id: Long = 0,

    @ColumnInfo(name = "reminder_id")
    val reminderId: Long = 0,

    @ColumnInfo(name = "reminder_description")
    val description: String = "",

    @ColumnInfo(name = "completion_status")
    val completionStatus: String = ReminderStatus.COMPLETED.name,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long = 0,
)

fun HistoryEntity.asDomainModel(): History =
    History(
        id = this.id,
        reminderId = this.reminderId,
        description = this.description,
        completionStatus = enumValueOf(this.completionStatus),
        completedAt = this.completedAt,
    )

fun List<HistoryEntity>.asDomainModel(): List<History> =
    this.map { it.asDomainModel() }