package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.di.MainDispatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import kotlin.properties.Delegates

@AndroidEntryPoint
abstract class AlarmReceiver(private val action: String) : BroadcastReceiver() {
    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var historyRepository: HistoryRepository

    @MainDispatcher
    @Inject
    lateinit var mainDispatcher: CoroutineDispatcher

    /**
     * A MainScope for use with launching coroutines and PendingResult.finish.
     */
    protected val coroutineScope: CoroutineScope by lazy {
        CoroutineScope(mainDispatcher + CoroutineName("AlarmReceiver"))
    }

    /**
     * The Reminder id associated with the Intent.
     */
    protected var reminderId by Delegates.notNull<Long>()

    final override fun onReceive(context: Context, intent: Intent) {
        if (action == intent.action) {
            reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, 0L)
            doReceive(context, intent)
        }
    }

    /**
     * Override in derived classes to implement onReceive behavior for the action
     * given to AlarmReceiver's constructor.
     */
    abstract fun doReceive(context: Context, intent: Intent)
}
