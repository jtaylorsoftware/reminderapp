package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.plus
import timber.log.Timber
import javax.inject.Inject
import kotlin.properties.Delegates

@AndroidEntryPoint
abstract class AlarmReceiver(private val action: String): BroadcastReceiver() {
    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var historyRepository: HistoryRepository

    /**
     * A MainScope for use with launching coroutines and PendingResult.finish.
     */
    protected val coroutineScope = MainScope() + CoroutineName("AlarmReceiver")

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
