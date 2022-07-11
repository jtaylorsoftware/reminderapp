package app.timetoremind.mobile.worker

import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.testing.TestListenableWorkerBuilder
import app.timetoremind.mobile.HiltTestActivity
import app.timetoremind.mobile.R
import app.timetoremind.mobile.data.AlarmRepository
import app.timetoremind.mobile.data.HistoryRepository
import app.timetoremind.mobile.data.ReminderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest

/**
 * Utility class that runs tests on workers in an empty Activity, using a custom
 * worker factory that will correctly construct workers using supplied dependencies.
 */
class WorkTestCoordinator(
    alarmRepository: AlarmRepository,
    reminderRepository: ReminderRepository,
    historyRepository: HistoryRepository
) {
    val factory = TestWorkerFactory(alarmRepository, reminderRepository, historyRepository)

    @ExperimentalCoroutinesApi
    inline fun <reified T: CoroutineWorker> runWorkTest(inputData: Data, crossinline block: suspend (worker: T) -> Unit) {
        val startActivityIntent = Intent.makeMainActivity(
            ComponentName(
                ApplicationProvider.getApplicationContext(),
                HiltTestActivity::class.java
            )
        ).putExtra(
            "androidx.fragment.app.testing.FragmentScenario.EmptyFragmentActivity.THEME_EXTRAS_BUNDLE_KEY",
            R.style.FragmentScenarioEmptyFragmentActivityTheme
        )

        ActivityScenario.launch<HiltTestActivity>(startActivityIntent).onActivity { activity ->
            val worker = TestListenableWorkerBuilder<T>(
                ApplicationProvider.getApplicationContext(),
                inputData = inputData,
            ).setWorkerFactory(factory).build()
            runBlockingTest {
                block(worker)
            }
        }
    }
}
