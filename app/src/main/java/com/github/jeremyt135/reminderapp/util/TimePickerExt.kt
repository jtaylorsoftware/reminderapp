package com.github.jeremyt135.reminderapp.util

import android.os.Build
import android.widget.TimePicker

/**
 * Extension property for TimePicker that allows reading and writing the hour and minute
 * as a pair, using SDK version appropriate properties on TimePicker.
 */
var TimePicker.time: Pair<Int, Int>
    get() {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Pair(this.hour, this.minute)
        } else {
            return Pair(this.currentHour, this.currentMinute)
        }
    }
    set(value) {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.hour = value.first
            this.minute = value.second
        } else {
            this.currentHour = value.first
            this.currentMinute = value.second
        }
    }
