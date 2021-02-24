package com.github.jeremyt135.reminderapp.screens.reminder.editor

import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

@VisibleForTesting(otherwise = VisibleForTesting.NONE)
@AndroidEntryPoint
class ReminderEditorTestActivity : AppCompatActivity() {
    val viewModel: ReminderEditorViewModel by viewModels()
}