package com.github.jeremyt135.reminderapp.util

import android.graphics.Color
import kotlin.random.Random

/**
 * A data structure that generates a cache of random colors. Once it has created
 * `cacheSize` colors it will recycle colors rather than creating new ones.
 */
class RandomColorCache(private val cacheSize: Int) {
    private var cache = ArrayDeque<Int>(initialCapacity = cacheSize)
    private var nextColors = ArrayDeque<Int>(initialCapacity = cacheSize)

    init {
        if (cacheSize <= 0) {
            throw IllegalArgumentException("RandomColorCache cacheSize must be positive")
        }
    }

    /**
     * Gets a random color value.
     */
    fun randomColor(): Int = if (nextColors.size > 0) {
        nextColor()
    } else {
        if (cache.size != cacheSize) {
            createColor()
        } else {
            flipCache()
            nextColor()
        }
    }

    private fun nextColor() = nextColors.removeAt(Random.nextInt(0, nextColors.size)).also {
        cache.addLast(it)
    }

    private fun flipCache() {
        val t = nextColors
        nextColors = cache
        cache = t
    }

    private fun createColor(): Int {
        val color = getRandomColor()
        cache.addLast(color)
        return color
    }
}

/**
 * Generates a random color within app palette.
 */
fun getRandomColor(): Int {
    val h = Random.nextInt(MIN_HUE, MAX_HUE).toFloat()
    val s = Random.nextInt(MIN_SATURATION, MAX_SATURATION) / 100f
    val v = Random.nextInt(MIN_VALUE, MAX_VALUE) / 100f
    return Color.HSVToColor(floatArrayOf(h, s, v))
}

private const val MIN_HUE = 180
private const val MAX_HUE = 320
private const val MIN_SATURATION = 40
private const val MAX_SATURATION = 70
private const val MIN_VALUE = 40
private const val MAX_VALUE = 80