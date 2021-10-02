package com.github.jeremyt135.reminderapp.theme

import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import timber.log.Timber

enum class ThemeSetting {
    DARK, LIGHT, SYSTEM;

    fun toAppNightMode() = when (this) {
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
 * Gets the current theme setting from shared preferences.
 */
fun SharedPreferences.getTheme(key: String) =
    ThemeSetting.valueOf(getString(key, ThemeSetting.SYSTEM.toString())!!)
