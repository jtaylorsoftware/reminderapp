package com.github.jeremyt135.reminderapp.util

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.*

fun DayOfWeek.getFullLocaleDisplay() = this.getDisplayName(TextStyle.FULL, Locale.getDefault())