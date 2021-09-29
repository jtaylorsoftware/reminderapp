package com.github.jeremyt135.reminderapp.screens.reminder.editor

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.data.domain.SnoozeRules
import com.github.jeremyt135.reminderapp.screens.preferences.PreferencesActivity
import com.github.jeremyt135.reminderapp.screens.reminder.editor.dialog.DayOfWeekDialogFragment
import com.github.jeremyt135.reminderapp.screens.reminder.editor.dialog.SnoozeOptionsDialogFragment
import com.github.jeremyt135.reminderapp.screens.reminder.editor.dialog.SoundPickerDialogFragment
import com.github.jeremyt135.reminderapp.util.*
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.time.DayOfWeek
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class ReminderEditorActivity : AppCompatActivity(), SoundPickerDialogFragment.DialogListener,
    SnoozeOptionsDialogFragment.DialogListener, DayOfWeekDialogFragment.DialogListener {
    private val viewModel: ReminderEditorViewModel by viewModels()

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    // Theme set at time Activity was created or last resumed
    private lateinit var localTheme: ThemeSetting

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminder_editor)

        setSupportActionBar(requireNotNull(findViewById(R.id.top_app_bar)))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel.navigateToReminders.asLiveData().observe(this) { event ->
            event.value?.let {
                // When "Cancel" button is pressed in editor, finish activity
                finish()
            }
        }

        localTheme = ThemeSetting.valueOf(
            sharedPreferences.getString(
                getString(R.string.preference_key_theme),
                ""
            )!!)
    }

    override fun onResume() {
        super.onResume()
        Timber.d("ON RESUME")
        // Handle any unknown theme change
        val theme = sharedPreferences.getString(getString(R.string.preference_key_theme), "")!!
        onThemeChanged(ThemeSetting.valueOf(theme))
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)

        prepareMenuForSettings(menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            // When "Up" button is pressed in toolbar, finish activity (similar to "back" button)
            android.R.id.home -> {
                finish()
                true
            }
            ITEM_SETTINGS -> {
                startActivity(Intent(this, PreferencesActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun onThemeChanged(theme: ThemeSetting) {
        if (onThemeChanged(theme, localTheme)) {
            localTheme = theme
        }
    }

    override fun onVibrateOptionChanged(vibrate: Boolean) {
        viewModel.onVibrateChanged(vibrate)
    }

    override fun onSoundUriChanged(soundUri: String) {
        viewModel.onSoundUriChanged(soundUri)
    }

    override fun onSnoozeRulesChanged(snoozeRules: SnoozeRules) {
        viewModel.onSnoozeRulesChanged(snoozeRules)
    }

    override fun onDaysOfWeekChanged(newValue: SortedSet<DayOfWeek>) {
        Timber.d("Days of week: $newValue")
        viewModel.onDaysOfWeekChanged(newValue)
    }
}