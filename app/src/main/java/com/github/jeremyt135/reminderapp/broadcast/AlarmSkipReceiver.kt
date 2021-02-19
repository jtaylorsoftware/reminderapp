package com.github.jeremyt135.reminderapp.broadcast

import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus

class AlarmSkipReceiver : AlarmStatusReceiver(ReminderStatus.SKIPPED, ACTION_ALARM_SKIP)