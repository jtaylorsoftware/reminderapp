package com.github.jeremyt135.reminderapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.github.jeremyt135.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RemindersActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}