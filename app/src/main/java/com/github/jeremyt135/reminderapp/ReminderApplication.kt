package com.github.jeremyt135.reminderapp

import android.app.Application
import com.github.jeremyt135.BuildConfig
import com.github.jeremyt135.reminderapp.broadcast.scheduleNextAlarm
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.di.AppCoroutineScope
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltAndroidApp
class ReminderApplication : Application() {
    @AppCoroutineScope
    @Inject
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var reminderRepository: ReminderRepository

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { delayedInit() }
    }

    private suspend fun delayedInit() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Perform cleanup and reschedule, which may be necessary due to a force stop
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        alarmRepository.deleteAllInPast(now.toEpochMilli())
        alarmRepository.clearAllScheduled()
        scheduleNextAlarm(this, alarmRepository, reminderRepository)
    }
}