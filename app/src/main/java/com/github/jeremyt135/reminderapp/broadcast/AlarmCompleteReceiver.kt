package com.github.jeremyt135.reminderapp.broadcast

import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus

class AlarmCompleteReceiver : AlarmStatusReceiver(ReminderStatus.COMPLETED, ACTION_ALARM_COMPLETE)