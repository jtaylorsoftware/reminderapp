package com.github.jeremyt135.reminderapp.theme

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.github.jeremyt135.R
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * An Activity that is aware of the theme preference and will recreate itself on changes.
 * It also creates an options menu entry for changing preferences.
 */
@AndroidEntryPoint
open class ThemeActivity: AppCompatActivity() {
    @Inject
    lateinit var sharedPreferences: SharedPreferences

    // Theme set at time Activity was created or last resumed
    private lateinit var localTheme: ThemeSetting

    private val themeKey: String by lazy {
        getString(R.string.preference_key_theme)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setDefaultPreferences()
        localTheme = sharedPreferences.getTheme(themeKey)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        onUiModeChanged(sharedPreferences.getTheme(themeKey))
    }

    override fun onResume() {
        super.onResume()
        // Handle any unknown theme change
        val theme = sharedPreferences.getTheme(themeKey)
        onThemeChanged(theme)
    }


    /**
     * Recreates the activity with the new theme if it is not the same as the local one.
     * @param theme The new theme
     */
    fun onThemeChanged(theme: ThemeSetting) {
        if (handleThemeChange(theme, localTheme)) {
            localTheme = theme
        }
    }

    /**
     * Recreates the activity with the new theme if it is not the same as the local one.
     * @param newTheme New theme to swap to
     * @param localTheme The previous theme, should usually be set once in onCreate and after onThemeChanged
     * @return Returns true if the theme changed.
     */
    private fun handleThemeChange(newTheme: ThemeSetting, localTheme: ThemeSetting): Boolean {
        val currentNightMode = AppCompatDelegate.getDefaultNightMode()
        if (newTheme == localTheme && currentNightMode == newTheme.toAppNightMode()){
            return false
        }

        Timber.d("Theme changed: $newTheme")
        AppCompatDelegate.setDefaultNightMode(newTheme.toAppNightMode())
        recreate()
        return true
    }

    /**
     * Recreates the activity with a new night mode value based on the theme setting.
     * If the theme setting is not set to ThemeSetting.SYSTEM, nothing is done and the current ThemeSetting
     * is left in place as the value used as the default night mode.
     * @param themeSetting The current theme setting.
     */
    private fun onUiModeChanged(themeSetting: ThemeSetting) {
        if (themeSetting != ThemeSetting.SYSTEM) {
            return
        }

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        recreate()
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