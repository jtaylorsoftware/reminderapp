package com.github.jeremyt135.reminderapp.data.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test


class SnoozeTest {
    @Test
    fun snoozeInterval_isNever_cannotRepeat(){
        val interval = SnoozeInterval.NEVER

        assertThat(interval.canStillRepeat(0)).isFalse()
        assertThat(interval.canNotRepeat(0)).isTrue()
        assertThat(interval.remainingSnoozes(0)).isEqualTo(0)
        assertThat(interval.neverRepeats).isTrue()
    }

    @Test
    fun snoozeInterval_isForever_alwaysRepeats(){
        val interval = SnoozeInterval.FOREVER

        assertThat(interval.canStillRepeat(Int.MAX_VALUE)).isTrue()
        assertThat(interval.canNotRepeat(Int.MAX_VALUE)).isFalse()
        assertThat(interval.remainingSnoozes(Int.MAX_VALUE)).isEqualTo(Int.MAX_VALUE)
        assertThat(interval.neverRepeats).isFalse()
    }

    @Test
    fun snoozeInterval_isFinite_repeatsAtMostThatMany(){
        val interval = SnoozeInterval.ONCE

        assertThat(interval.canStillRepeat(0)).isTrue()
        assertThat(interval.canNotRepeat(0)).isFalse()
        assertThat(interval.canStillRepeat(1)).isFalse()
        assertThat(interval.canNotRepeat(1)).isTrue()

        assertThat(interval.remainingSnoozes(0)).isEqualTo(1)
        assertThat(interval.remainingSnoozes(1)).isEqualTo(0)

        assertThat(interval.neverRepeats).isFalse()
    }

    @Test
    fun snoozeInterval_toString_appendsTime(){
        val once = SnoozeInterval.ONCE
        assertThat(once.toString()).endsWith("time")

        val three = SnoozeInterval.THREE
        assertThat(three.toString()).endsWith("times")
    }
}