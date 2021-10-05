package com.github.jeremyt135.reminderapp.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import com.github.jeremyt135.reminderapp.di.DefaultDispatcher
import com.github.jeremyt135.reminderapp.service.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * AlarmActionReceiver receives one of:
 *
 *  - ACTION_ALARM_COMPLETE,
 *  - ACTION_ALARM_SKIP,
 *  - ACTION_ALARM_SNOOZE,
 *
 *  and creates a WorkRequest for an AlarmActionHandler (CoroutineWorker)
 *  to handle the received action. Typically the receiver runs
 *  as a result of the user pressing a button on the AlarmService
 *  notification; other classes directly create the work they want
 *  to start.
 */
@AndroidEntryPoint
class AlarmActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var timerManager: AlarmTimerManager

    @AppCoroutineScope
    @Inject
    lateinit var appCoroutineScope: CoroutineScope

    @DefaultDispatcher
    @Inject
    lateinit var dispatcher: CoroutineDispatcher

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    private val actions = arrayOf(
        ACTION_ALARM_COMPLETE,
        ACTION_ALARM_SKIP,
        ACTION_ALARM_SNOOZE,
    )

    private lateinit var pendingResult: PendingResult

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null || intent.action !in actions) {
            Timber.e("AlarmActionReceiver called with incorrect action: ${intent.action}")
            return
        }

        // Stop the AlarmService responsible for media playback & notification
        stopAlarmService(context)

        // Stop the AlarmTimer so it doesn't auto snooze
        if (timerManager.timer?.stop() == false) {
            Timber.e("Could not stop AlarmTimer in receiver")
            return
        }

        // Do the alarm action if given a valid id
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: ""
        if (reminderId.isNotEmpty()) {
            pendingResult = goAsync()
            doAlarmAction(context, intent.action!!, reminderId)
        } else {
            Timber.e("Could not get reminder")
            AlarmSchedulingService.startScheduleNext(context)
        }
    }

    private fun stopAlarmService(context: Context) {
        Timber.d("Requesting AlarmService stops")
        AlarmMediaService.requestStop(context)
    }

    private fun doAlarmAction(context: Context, action: String, reminderId: String) {
        appCoroutineScope.launch(dispatcher) {
            try {
                // Abort if couldn't find the reminder
                val reminder = reminderRepository.findById(reminderId)
                    ?: throw IllegalMonitorStateException("Could not find reminder with id $reminderId")

                when (action) {
                    ACTION_ALARM_SKIP -> {
                        AlarmHistoryService.start(context, reminder, ReminderStatus.SKIPPED)
                        AlarmSchedulingService.startScheduleNewAlarm(context, reminder)
                    }
                    ACTION_ALARM_COMPLETE -> {
                        AlarmHistoryService.start(context, reminder, ReminderStatus.COMPLETED)
                        AlarmSchedulingService.startScheduleNewAlarm(context, reminder)
                    }
                    ACTION_ALARM_SNOOZE -> {
                        AlarmSnoozeService.start(context, reminder)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e)
                AlarmSchedulingService.startScheduleNext(context)
                if (e is CancellationException) {
                    throw e
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}