package com.github.jeremyt135.reminderapp

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
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
import com.github.jeremyt135.reminderapp.settings.getBaseSharedPreferences
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

        // Try to reschedule each reminder which may be necessary due to a force stop
        rescheduleReminders(this, reminderRepository, alarmRepository, historyRepository)
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

    /**
     * Sets the default theme preferences.
     */
    private fun setDefaultPreferences() {
        val preferencesName = getString(R.string.preference_file_name)

        PreferenceManager.setDefaultValues(
            this,
            preferencesName,
            Context.MODE_PRIVATE,
            R.xml.preference_screen,
            false
        )
    }
}