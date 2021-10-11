package app.timetoremind.mobile.util

import java.time.*
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
fun Long.toLocalDateTime(atZone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(atZone).toLocalDateTime()
        .truncatedTo(ChronoUnit.MINUTES)

/**
 * Returns a LocalDate representing an epoch time in milliseconds.
 */
fun Long.toLocalDate(atZone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(atZone).toLocalDate()

/**
 * Returns milliseconds since epoch for a LocalDateTime, truncated to minutes.
 */
fun LocalDateTime.toEpochMilli(atZone: ZoneId = ZoneId.systemDefault()): Long =
    truncatedTo(ChronoUnit.MINUTES).atZone(atZone).toInstant().toEpochMilli()

/**
 * Returns current LocalDateTime, truncated to minutes
 */
fun currentLocalDateTime(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)

/**
 * Returns current LocalTime, truncated to minutes
 */
fun currentLocalTime(): LocalTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES)

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
 * Returns a String containing the short localized date display value for a LocalDate
 */
fun LocalDate.getShortLocalDateDisplay(): String = format(
    DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
)

/**
 * Returns a String containing the full localized name of a DayOfWeek
 */
fun DayOfWeek.getFullLocaleDisplay(): String = getDisplayName(TextStyle.FULL, Locale.getDefault())


/**
 * Returns a String containing the short localized name of a DayOfWeek
 */
fun DayOfWeek.getShortLocaleDisplay(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())


