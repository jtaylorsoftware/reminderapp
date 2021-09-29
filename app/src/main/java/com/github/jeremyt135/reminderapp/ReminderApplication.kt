package com.github.jeremyt135.reminderapp

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.hilt.work.HiltWorkerFactory
import androidx.preference.PreferenceManager
import com.github.jeremyt135.BuildConfig
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.broadcast.rescheduleReminders
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
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
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { delayedInit() }
    }

    private suspend fun delayedInit() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Try to reschedule each reminder which may be necessary due to a force stop
        rescheduleReminders(this, reminderRepository, alarmRepository)
    }

    override fun getWorkManagerConfiguration() =
        androidx.work.Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}