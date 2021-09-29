package com.github.jeremyt135.reminderapp.screens.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.util.ThemeSetting
import com.github.jeremyt135.reminderapp.util.onThemeChanged
import timber.log.Timber

class PreferencesActivity : AppCompatActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var localTheme: ThemeSetting
    private lateinit var themeKey: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate")

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        themeKey = getString(R.string.preference_key_theme)
        sharedPreferences =
            getSharedPreferences(getString(R.string.preference_file_name), Context.MODE_PRIVATE)
        localTheme = ThemeSetting.valueOf(sharedPreferences.getString(themeKey, "")!!)

        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, PreferencesFragment())
            .commit()
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)

    }

    private fun onThemeChanged(theme: ThemeSetting) {
        Timber.d("onThemeChanged: $theme")
        if (onThemeChanged(theme, localTheme)) {
            localTheme = theme
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            // When "Up" button is pressed in toolbar, finish activity (similar to "back" button)
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == themeKey) {
            onThemeChanged(ThemeSetting.valueOf(sharedPreferences.getString(themeKey, "")!!))
        }
    }
}