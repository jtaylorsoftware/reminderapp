package com.github.jeremyt135.reminderapp.util

import java.util.concurrent.atomic.AtomicInteger

object NotificationId {
    /**
     * First reserved ID for other notifications.
     */
    const val FIRST_ID = 1

    /**
     * First ID for usage by Alarm notifications.
     */
    private const val FIRST_ID_ALARMS = 100

    private val _next = AtomicInteger(FIRST_ID_ALARMS)

    val next: Int
        get() = _next.incrementAndGet()
}