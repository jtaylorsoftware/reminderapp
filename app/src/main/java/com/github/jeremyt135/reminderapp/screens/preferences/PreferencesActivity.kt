package com.github.jeremyt135.reminderapp.screens.preferences

import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.theme.ThemeActivity
import com.github.jeremyt135.reminderapp.theme.ThemeSetting

class PreferencesActivity : ThemeActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val themeKey: String by lazy {
        getString(R.string.preference_key_theme)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

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
            super.handleThemeChange(ThemeSetting.valueOf(sharedPreferences.getString(themeKey, "")!!))
        }
    }
}