package com.github.jeremyt135.reminderapp.data.domain

import com.github.jeremyt135.reminderapp.data.DomainModel
import com.github.jeremyt135.reminderapp.data.local.AlarmEntity
import com.github.jeremyt135.reminderapp.util.NotificationId
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import java.time.LocalDateTime

data class Alarm(
    var id: Long = 0L,
    val reminderId: String,
    val triggerAt: LocalDateTime = currentLocalDateTime(),
    val scheduled: Boolean? = null,
    val notificationId: Int = NotificationId.next,
    val snoozeInterval: SnoozeInterval = SnoozeInterval.FOREVER,
    val snoozeDuration: SnoozeDuration = SnoozeDuration.FIVE,
    val snoozeCount: Int = 0
) : DomainModel<AlarmEntity> {
    override fun asDataModel(): AlarmEntity = AlarmEntity(
        id = this.id,
        reminderId = this.reminderId,
        triggerAt = this.triggerAt,
        scheduled = this.scheduled,
        notificationId = this.notificationId,
        snoozeInterval = this.snoozeInterval,
        snoozeDuration = this.snoozeDuration,
        snoozeCount = this.snoozeCount
    )

    val remainingSnoozes = snoozeInterval.remainingSnoozes(snoozeCount)
}