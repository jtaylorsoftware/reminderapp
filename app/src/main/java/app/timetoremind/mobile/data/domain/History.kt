package app.timetoremind.mobile.data.domain

import app.timetoremind.mobile.data.DomainModel
import app.timetoremind.mobile.data.local.HistoryEntity
import app.timetoremind.mobile.util.getShortLocalDateDisplay
import app.timetoremind.mobile.util.getShortLocalTimeDisplay
import app.timetoremind.mobile.util.toLocalDateTime
import app.timetoremind.mobile.util.uuid

data class History(
    val id: String = uuid(),
    val reminderId: String = "",
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
