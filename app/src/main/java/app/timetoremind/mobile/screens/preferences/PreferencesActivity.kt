package app.timetoremind.mobile.screens.preferences

import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.preference.PreferenceManager
import app.timetoremind.mobile.R
import app.timetoremind.mobile.theme.ThemeSetting
import app.timetoremind.mobile.work.AlarmSchedulingWorker
import timber.log.Timber

class PreferencesActivity : AppCompatActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val themeKey: String by lazy {
        getString(R.string.preference_key_theme)
    }
    private val scheduleIntervalKey: String by lazy {
        getString(R.string.preference_key_schedule_interval)
    }
    private val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preferences)

        val navHostFragment =
            requireNotNull(supportFragmentManager.findFragmentById(R.id.nav_host_fragment_preferences))
        navController = navHostFragment.findNavController()
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            // When "Up" button is pressed in toolbar, finish activity (similar to "back" button)
            android.R.id.home -> {
                if (navController.currentDestination?.id == R.id.fragment_preferences) {
                    finish()
                    true
                } else {
                    onSupportNavigateUp()
                }
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            themeKey -> {
                handleThemeChange(
                    sharedPreferences.getString(
                        themeKey,
                        ThemeSetting.defaultValue().toString()
                    )!!
                )
            }
            scheduleIntervalKey -> {
                AlarmSchedulingWorker.cancelWork(this)
                val interval = sharedPreferences.getInt(
                    scheduleIntervalKey,
                    AlarmSchedulingWorker.DEFAULT_INTERVAL_MINUTES
                ).toLong()
                AlarmSchedulingWorker.enqueue(this, interval)
                Timber.d("Updated interval to $interval minutes")
            }
        }
    }

    private fun handleThemeChange(newValue: String) {
        val newTheme = ThemeSetting.valueOf(newValue).toAppNightMode()
        AppCompatDelegate.setDefaultNightMode(newTheme)
    }
}