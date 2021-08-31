package com.github.jeremyt135.reminderapp.data.domain

import com.github.jeremyt135.reminderapp.data.DomainModel
import com.github.jeremyt135.reminderapp.data.local.AlarmEntity
import com.github.jeremyt135.reminderapp.util.NotificationId
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import java.time.LocalDateTime
import kotlin.math.min

data class Alarm(
    var id: Long = 0L,
    val reminderId: String,
    val triggerAt: LocalDateTime = currentLocalDateTime(),
    val scheduled: Boolean? = null,
    val notificationId: Int = NotificationId.next,
    val maxSnoozeRepeats: Long = SnoozeRules.DEFAULT_SNOOZE_REPEATS,
    val snoozeDuration: Long = SnoozeRules.DEFAULT_SNOOZE_DURATION,
    val snoozeCount: Long = 0
) : DomainModel<AlarmEntity> {
    override fun asDataModel(): AlarmEntity = AlarmEntity(
        id = this.id,
        reminderId = this.reminderId,
        triggerAt = this.triggerAt,
        scheduled = this.scheduled,
        notificationId = this.notificationId,
        maxSnoozeRepeats = this.maxSnoozeRepeats,
        snoozeDuration = this.snoozeDuration,
        snoozeCount = this.snoozeCount
    )

    val snoozeRules = SnoozeRules(maxRepeats = maxSnoozeRepeats, duration = snoozeDuration)

    val remainingSnoozes = min(0, maxSnoozeRepeats - snoozeCount)
}