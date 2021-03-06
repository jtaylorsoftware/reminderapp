package com.github.jeremyt135.reminderapp.data.domain

import com.github.jeremyt135.reminderapp.data.DomainModel
import com.github.jeremyt135.reminderapp.data.local.HistoryEntity
import com.github.jeremyt135.reminderapp.util.getShortLocalDateDisplay
import com.github.jeremyt135.reminderapp.util.getShortLocalTimeDisplay
import com.github.jeremyt135.reminderapp.util.toLocalDateTime

data class History(
    val id: Long = 0,
    val reminderId: Long = 0,
    val description: String = "",
    val completionStatus: ReminderStatus = ReminderStatus.PENDING,
    val completedAt: Long = 0,
) : DomainModel<HistoryEntity> {
    private val completionLocalDateTime = completedAt.toLocalDateTime()

    fun getTimeDisplayString(): String = completionLocalDateTime.getShortLocalTimeDisplay()

    fun getDateDisplayString(): String = completionLocalDateTime.getShortLocalDateDisplay()

    override fun asDataModel(): HistoryEntity = HistoryEntity(
        id = this.id,
        reminderId = this.reminderId,
        description = this.description,
        completionStatus = this.completionStatus.name,
        completedAt = this.completedAt,
    )
}
