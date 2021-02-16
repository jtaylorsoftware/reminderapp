package com.github.jeremyt135.reminderapp.data.domain

import com.github.jeremyt135.reminderapp.data.DomainModel
import com.github.jeremyt135.reminderapp.data.local.AlarmEntity

data class Alarm(
    var id: Long = 0L,
    val reminderId: Long,
    val triggerAt: Long,
) : DomainModel<AlarmEntity> {
    override fun asDataModel(): AlarmEntity = AlarmEntity(
        id = this.id,
        reminderId = this.reminderId,
        triggerAt = this.triggerAt
    )
}