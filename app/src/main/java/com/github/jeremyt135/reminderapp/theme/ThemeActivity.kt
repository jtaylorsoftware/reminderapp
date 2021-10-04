package com.github.jeremyt135.reminderapp.theme

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.ReminderApplication
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * An Activity that is aware of the theme preference and will recreate itself on changes.
 * It also creates an options menu entry for changing preferences.
 */
@AndroidEntryPoint
open class ThemeActivity: AppCompatActivity() {

    protected val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    // Theme set at time Activity was created or last resumed
    private lateinit var localTheme: ThemeSetting

    private val themeKey: String by lazy {
        getString(R.string.preference_key_theme)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localTheme = sharedPreferences.getTheme(themeKey)
        setInitialTheme(localTheme)
    }

    override fun onResume() {
        super.onResume()
        // Handle any unknown theme change
        val theme = sharedPreferences.getTheme(themeKey)
        handleThemeChange(theme)
    }

    /**
     * Recreates the activity with the new theme if it is not the same as the local one.
     * @param theme The new theme
     */
    protected fun handleThemeChange(theme: ThemeSetting) {
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
        setDefaultNightMode(newTheme.toAppNightMode())
        return true
    }

    private fun setInitialTheme(themeSetting: ThemeSetting) {
        setDefaultNightMode(themeSetting.toAppNightMode())
    }

    private fun setDefaultNightMode(mode: Int) {
        (application as? ReminderApplication)?.run {
            setDefaultNightMode(mode)
        }
    }
}