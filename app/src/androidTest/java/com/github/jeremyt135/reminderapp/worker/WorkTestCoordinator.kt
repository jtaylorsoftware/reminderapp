package com.github.jeremyt135.reminderapp.worker

import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.testing.TestListenableWorkerBuilder
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.HiltTestActivity
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
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
