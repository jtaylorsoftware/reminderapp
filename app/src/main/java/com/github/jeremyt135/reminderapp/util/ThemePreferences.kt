package com.github.jeremyt135.reminderapp.util

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import timber.log.Timber

enum class ThemeSetting {
    DARK, LIGHT, SYSTEM;

    fun toAppNightMode() = when(this) {
        DARK -> AppCompatDelegate.MODE_NIGHT_YES
        LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

//    companion object {
//        fun fromAppNightMode(nightMode: Int) = when(nightMode) {
//            AppCompatDelegate.MODE_NIGHT_YES -> DARK
//            AppCompatDelegate.MODE_NIGHT_NO -> LIGHT
//            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> SYSTEM
//            else -> SYSTEM
//        }
//    }
}

/**
 * Recreates the activity with the new theme if it is not the same as the local one.
 * @param newTheme New theme to swap to
 * @param localTheme The previous theme, should usually be set once in onCreate and after onThemeChanged
 * @return Returns true if the theme changed.
 */
fun AppCompatActivity.onThemeChanged(newTheme: ThemeSetting, localTheme: ThemeSetting): Boolean {
    if (newTheme == localTheme){
        return false
    }

    Timber.d("Theme changed: $newTheme")
    AppCompatDelegate.setDefaultNightMode(newTheme.toAppNightMode())
    recreate()
    return true
}