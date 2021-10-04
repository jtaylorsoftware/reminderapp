package com.github.jeremyt135.reminderapp

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.preference.PreferenceManager
import com.github.jeremyt135.BuildConfig
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.broadcast.rescheduleReminders
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.HistoryRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import com.github.jeremyt135.reminderapp.work.AlarmSchedulingWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ReminderApplication : Application(), androidx.work.Configuration.Provider {
    @AppCoroutineScope
    @Inject
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var historyRepository: HistoryRepository

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        setDefaultPreferences()
        applicationScope.launch { delayedInit() }
    }

    private suspend fun delayedInit() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        doFirstTimeSetup()
    }

    override fun getWorkManagerConfiguration() =
        androidx.work.Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Changes the default night mode for the app.
     */
    fun setDefaultNightMode(mode: Int) {
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private suspend fun doFirstTimeSetup() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val key = getString(R.string.preference_key_first_startup)
        val isFirstStartup = preferences.getBoolean(key, true)

        if (isFirstStartup) {
            Timber.d("Performing first time setup")

            // On first startup, do not reschedule reminders immediately
            with(preferences.edit()) {
                putBoolean(key, false)
                apply()
            }

            val scheduleInterval =
                preferences
                    .getInt(getString(R.string.preference_key_schedule_interval), 30)
                    .toLong()
            // Start periodic work requests
            AlarmSchedulingWorker.enqueue(this, scheduleInterval)
        } else {
            // On next startups, try to reschedule each reminder which may be necessary due to a force stop
            rescheduleReminders(this, reminderRepository, alarmRepository, historyRepository)
        }
    }

    /**
     * Sets the default theme preferences.
     */
    private fun setDefaultPreferences() {
        PreferenceManager.setDefaultValues(
            this,
            R.xml.preference_screen,
            false
        )
    }
}