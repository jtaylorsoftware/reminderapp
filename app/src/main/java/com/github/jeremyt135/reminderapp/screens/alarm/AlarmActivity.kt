package com.github.jeremyt135.reminderapp.screens.alarm

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.asLiveData
import com.github.jeremyt135.R
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class AlarmActivity : AppCompatActivity() {
    private val viewModel: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.d("Created AlarmActivity")

        /**
         * Ensure that desired screen behavior is achieved across SDK versions
         * - Alarm should turn screen on, show even if locked, and dismiss the keyguard/lockscreen
         */
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        } else {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(
                this,
                null
            )
        }

        /**
         * React to viewModel.finish by closing activity
         */
        viewModel.finished.flow.asLiveData().observe(this) { event ->
            event.value?.let {
                Timber.d("Finishing AlarmActivity")
                this@AlarmActivity.finish()
            }
        }

        setContentView(R.layout.activity_alarm)
        supportActionBar?.hide()
    }

    override fun onResume() {
        super.onResume()
        Timber.d("Resuming AlarmActivity")
    }

}