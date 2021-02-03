package com.github.jeremyt135.reminderapp.util

import android.view.View
import android.widget.TimePicker
import androidx.test.espresso.matcher.BoundedMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher
import java.time.LocalTime

/**
 * Returns a matcher that matches TimePicker based on its hour and time property values
 */
fun withTime(expectedTime: LocalTime): Matcher<View> =
    object : BoundedMatcher<View, TimePicker>(TimePicker::class.java) {
        override fun describeTo(description: Description?) {
            description?.appendText("matches TimePicker time: $expectedTime")
        }

        override fun matchesSafely(item: TimePicker?): Boolean {
            return item?.let{ picker ->
                val (actualHour, actualMinute) = picker.time
                actualHour == expectedTime.hour && actualMinute == expectedTime.minute
            } ?: false
        }
    }
