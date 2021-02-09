package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import javax.inject.Inject

abstract class HiltBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}

abstract class AlarmReceiver : HiltBroadcastReceiver() {
    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Suppress("REDUNDANT_MODIFIER")
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }
}
