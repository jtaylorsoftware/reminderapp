package com.github.jeremyt135.reminderapp.data.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class SnoozeTest {
    @Test
    fun `when max repeats is 0, neverRepeats is true`() {
        val rules = SnoozeRules(0, 30)
        assertThat(rules.neverRepeats).isTrue()
    }

    @Test
    fun `when max repeats is Long MAX_VALUE, alwaysRepeats is true`() {
        val rules = SnoozeRules(Long.MAX_VALUE, 30)
        assertThat(rules.alwaysRepeats).isTrue()
    }

    @Test
    fun `when constructed with negative duration, throws IllegalStateException`() {
        assertThrows(IllegalStateException::class.java) {
            SnoozeRules(0, -1)
        }
    }

    @Test
    fun `when constructed with negative maxRepeats, throws IllegalStateException`() {
        assertThrows(IllegalStateException::class.java) {
            SnoozeRules(-1, 5)
        }
    }

    @Test
    fun `canSnooze returns true if alwaysRepeats is true`() {
        val rules = SnoozeRules(Long.MAX_VALUE, 30)
        assertThat(rules.canSnooze(0)).isEqualTo(rules.alwaysRepeats)
    }

    @Test
    fun `canSnooze returns false if neverRepeats is true`() {
        val rules = SnoozeRules(0, 30)
        assertThat(rules.canSnooze(0)).isNotEqualTo(rules.neverRepeats)
    }

    @Test
    fun `canSnooze returns true if it can repeat and argument is not equal to max`() {
        val rules = SnoozeRules(2, 30)
        assertThat(rules.canSnooze(1)).isTrue()
    }

    @Test
    fun `canSnooze returns false if it can repeat and argument is equal to max`() {
        val rules = SnoozeRules(1, 30)
        assertThat(rules.canSnooze(1)).isFalse()
    }
}