package com.github.jeremyt135.reminderapp.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Utility date & time methods that return values expected by the app.
 *
 * All times are truncated to the minute to match the expectation of
 * the saved Reminders having an alarm start on the minute.
 *
 */

/**
 * Returns a LocalDateTime representing a unix epoch time in milliseconds, truncated to minutes.
 */
fun Long.toLocalDateTime(): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()
        .truncatedTo(ChronoUnit.MINUTES)

/**
 * Returns milliseconds since epoch for a LocalDateTime, truncated to minutes.
 */
fun LocalDateTime.toEpochMilli(): Long =
    truncatedTo(ChronoUnit.MINUTES).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * Returns current LocalDateTime, truncated to minutes
 */
fun currentLocalDateTime(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)

/**
 * Returns current milliseconds since epoch, truncated to minutes.
 */
fun currentEpochMilli(): Long = Instant.now().truncatedTo(ChronoUnit.MINUTES).toEpochMilli()

/**
 * Returns a String containing the short localized time display value for a LocalDateTime
 */
fun LocalDateTime.getShortLocalTimeDisplay(): String = format(
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
)

/**
 * Returns a String containing the short localized date display value for a LocalDateTime
 */
fun LocalDateTime.getShortLocalDateDisplay(): String = format(
    DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
)

/**
 * Returns a String containing the full localized name of a DayOfWeek
 */
fun DayOfWeek.getFullLocaleDisplay(): String = getDisplayName(TextStyle.FULL, Locale.getDefault())


