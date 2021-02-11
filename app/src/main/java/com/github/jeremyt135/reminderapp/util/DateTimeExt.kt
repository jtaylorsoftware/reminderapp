package com.github.jeremyt135.reminderapp.util

import java.time.*
import java.time.format.TextStyle
import java.util.*

fun Long.toLocalDateTime(): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

fun LocalDateTime.toEpochMilli(): Long =
    this.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun DayOfWeek.getFullLocaleDisplay() = this.getDisplayName(TextStyle.FULL, Locale.getDefault())

