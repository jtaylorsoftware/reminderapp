package app.timetoremind.mobile.theme

import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate

enum class ThemeSetting {
    DARK, LIGHT, SYSTEM, BATTERY;

    fun toAppNightMode() = when (this) {
        DARK -> AppCompatDelegate.MODE_NIGHT_YES
        LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        BATTERY -> AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
    }

    companion object {
        fun defaultValue(): ThemeSetting = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            BATTERY
        } else {
            SYSTEM
        }
    }
}

/**
 * Gets the current theme setting from shared preferences.
 */
fun SharedPreferences.getTheme(key: String): ThemeSetting {
    val defaultValue = ThemeSetting.defaultValue()
    return try {
        getString(key, defaultValue.toString())?.let {
            ThemeSetting.valueOf(it)
        } ?: defaultValue
    } catch (e: Exception) {
        defaultValue
    }
}