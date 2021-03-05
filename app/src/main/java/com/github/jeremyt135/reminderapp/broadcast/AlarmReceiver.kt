package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@AndroidEntryPoint
abstract class AlarmReceiver(vararg actions: String) : BroadcastReceiver() {
    private val actions = actions.asList()

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @AppCoroutineScope
    @Inject
    lateinit var applicationScope: CoroutineScope

    final override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in actions) {
            doReceive(context, intent)
        }
    }

    /**
     * Override in derived classes to implement onReceive behavior for the action
     * given to AlarmReceiver's constructor.
     */
    abstract fun doReceive(context: Context, intent: Intent)
}
