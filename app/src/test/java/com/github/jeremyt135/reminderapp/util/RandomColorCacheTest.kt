package com.github.jeremyt135.reminderapp.util

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RandomColorCacheTest {
    private lateinit var cache: RandomColorCache

    // Can tune as desired to stress test the color generation,
    // but there shouldn't be any with small values
    private val cacheSize = 250

    @Before
    fun setup(){
        cache = RandomColorCache(cacheSize)
    }

    @Test
    fun randomColorCache_createsCacheSizeColors() {
        val createdColors = getColors(cache)
        assertThat(createdColors).containsNoDuplicates()
    }

    @Test
    fun randomColorCache_recyclesColors() {
        // Get initial values
        val createdColors = getColors(cache)

        // Get values again, they should be recycled and the same
        val recycledColors = getColors(cache)
        assertThat(recycledColors).containsExactlyElementsIn(createdColors)
    }

    private fun getColors(randomColorCache: RandomColorCache): List<Int> {
        val createdColors = mutableListOf<Int>()
        while (createdColors.size < cacheSize) {
            createdColors.add(randomColorCache.randomColor())
        }
        return createdColors
    }
}