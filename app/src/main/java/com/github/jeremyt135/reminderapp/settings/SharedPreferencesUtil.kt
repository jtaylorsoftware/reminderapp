package com.github.jeremyt135.reminderapp.settings

import android.content.Context
import com.github.jeremyt135.R

/**
 * Returns a SharedPreferences instance through which one can access the base preferences for
 * the application, including Theme settings.
 */
fun Context.getBaseSharedPreferences() =
    getSharedPreferences(getString(R.string.preference_file_name), Context.MODE_PRIVATE)