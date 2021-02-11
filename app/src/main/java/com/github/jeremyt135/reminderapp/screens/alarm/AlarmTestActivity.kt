package com.github.jeremyt135.reminderapp.screens.alarm

import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

@VisibleForTesting(otherwise = VisibleForTesting.NONE)
@AndroidEntryPoint
class AlarmTestActivity : AppCompatActivity() {
    val viewModel: AlarmViewModel by viewModels()
}