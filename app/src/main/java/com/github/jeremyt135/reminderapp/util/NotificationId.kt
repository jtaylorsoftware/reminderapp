package com.github.jeremyt135.reminderapp.util

import java.util.concurrent.atomic.AtomicInteger

object NotificationId {
    private val _next = AtomicInteger(0)

    val next: Int
        get() = _next.incrementAndGet()
}