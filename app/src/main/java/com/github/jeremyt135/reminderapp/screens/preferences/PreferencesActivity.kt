package com.github.jeremyt135.reminderapp.screens.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.theme.ThemeActivity
import com.github.jeremyt135.reminderapp.theme.ThemeSetting

class PreferencesActivity : ThemeActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var localTheme: ThemeSetting
    private lateinit var themeKey: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            super.onThemeChanged(ThemeSetting.valueOf(sharedPreferences.getString(themeKey, "")!!))
        }
    }
}