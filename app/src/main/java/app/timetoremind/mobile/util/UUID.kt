package app.timetoremind.mobile.util

import java.util.*

/**
 * Utility for generating random UUID and returning them as String values
 */
fun uuid(): String  = UUID.randomUUID().toString()
