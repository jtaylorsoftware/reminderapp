package app.timetoremind.mobile.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import app.timetoremind.mobile.data.AlarmRepository
import app.timetoremind.mobile.data.HistoryRepository
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.service.AlarmSchedulingService
import app.timetoremind.mobile.work.AlarmSchedulingWorker

/**
 * Factory class for creating worker classes in tests, because apparently HiltWorkerFactory just
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
            AlarmSchedulingService::class.java.name -> AlarmSchedulingWorker(
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