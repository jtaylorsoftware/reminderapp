package app.timetoremind.mobile

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.preference.PreferenceManager
import app.timetoremind.mobile.di.AppCoroutineScope
import app.timetoremind.mobile.service.AlarmSchedulingService
import app.timetoremind.mobile.theme.getTheme
import app.timetoremind.mobile.work.AlarmSchedulingWorker
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
    lateinit var workerFactory: HiltWorkerFactory

    private val themeKey: String by lazy {
        getString(R.string.preference_key_theme)
    }

    override fun onCreate() {
        super.onCreate()
        setDefaultPreferences()
        loadDarkModePreference()
        applicationScope.launch { delayedInit() }
    }

    private fun delayedInit() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        doSetup()
    }

    override fun getWorkManagerConfiguration() =
        androidx.work.Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun doSetup() {
        val acceptedTermsKey = getString(R.string.preference_key_accepted_terms)
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val acceptedTerms = preferences.getBoolean(acceptedTermsKey, false)

        // Don't start using app functionality if user hasn't accepted terms
        if (!acceptedTerms) {
            return
        }

        // User has accepted terms, so do app functionality

        val scheduleInterval =
            preferences
                .getInt(getString(R.string.preference_key_schedule_interval), 30)
                .toLong()

        // Start unique periodic work request which may not be started due to system
        // killing background processes
        AlarmSchedulingWorker.enqueue(this, scheduleInterval)

        // Do a reschedule now which may be necessary due to a force stop
        AlarmSchedulingService.startRescheduleAll(this)
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

    /**
     * Loads the dark/night theme preference and immediately sets it as default
     */
    private fun loadDarkModePreference() {
        val preference = PreferenceManager.getDefaultSharedPreferences(this).getTheme(themeKey)
        AppCompatDelegate.setDefaultNightMode(preference.toAppNightMode())
    }
}