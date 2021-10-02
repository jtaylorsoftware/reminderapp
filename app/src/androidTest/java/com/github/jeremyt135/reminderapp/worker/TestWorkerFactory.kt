package com.github.jeremyt135.reminderapp.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.work.AlarmActionHandler

/**
 * Factory class for creating AlarmActionHandler in tests, because apparently HiltWorkerFactory just
 * doesn't care about UninstallModules.
 */
class TestWorkerFactory(
    private val alarmRepository: AlarmRepository,
    private val reminderRepository: ReminderRepository,
    private val historyRepository: HistoryRepository
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            AlarmActionHandler::class.java.name -> AlarmActionHandler(
                appContext,
                workerParameters,
                alarmRepository,
                reminderRepository,
                historyRepository
            )
            else -> return null
        }
    }
}