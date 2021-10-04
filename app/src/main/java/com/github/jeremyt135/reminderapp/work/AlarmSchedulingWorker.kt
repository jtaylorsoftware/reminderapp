package com.github.jeremyt135.reminderapp.work

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.broadcast.rescheduleReminders
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Handles periodic rescheduling to ensure alarms go off consistently.
 */
@HiltWorker
class AlarmSchedulingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val alarmRepository: AlarmRepository,
    private val reminderRepository: ReminderRepository,
    private val historyRepository: HistoryRepository,
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo())
        rescheduleReminders(
            applicationContext,
            reminderRepository,
            alarmRepository,
            historyRepository
        )
        return Result.success()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val channel = applicationContext.getString(R.string.scheduling_notification_channel_id)
        val title = "Reminders scheduling"
        val appName = applicationContext.getString(R.string.app_name)
        val notification = NotificationCompat.Builder(applicationContext, channel).apply {
            setContentTitle(title)
            setSmallIcon(R.drawable.ic_scheduling_notification)
            setContentText("\"$appName\" is performing periodic rescheduling of your reminders.")
            setPriority(NotificationCompat.PRIORITY_LOW)
            setCategory(NotificationCompat.CATEGORY_SERVICE)
            setOngoing(true)
        }.build()
        return ForegroundInfo(Int.MAX_VALUE, notification)
    }

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 30

        /**
         * Enqueues periodic work to go off at a minutes-based interval.
         */
        fun enqueue(context: Context, intervalMinutes: Long) =
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                createPeriodicRequest(intervalMinutes)
            )

        /**
         * Stops any previous enqueued work so it can be updated with a new interval.
         */
        fun cancelWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        private fun createPeriodicRequest(intervalMinutes: Long): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<AlarmSchedulingWorker>(
                intervalMinutes,
                TimeUnit.MINUTES
            ).apply {
                setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
                setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            }.build()

        private const val WORK_NAME = "AlarmSchedulingWorker"
    }
}