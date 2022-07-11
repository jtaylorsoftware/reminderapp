package app.timetoremind.mobile.service

import app.timetoremind.sharedtest.MainCoroutineRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class AlarmTimerTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    @Test
    fun timer_finishes_performsAction() = runTest {
        var actionData = 0
        val timer = AlarmTimer(this){
            actionData += 1
        }
        timer.start()
        assertThat(timer.isStarted).isTrue()
        advanceTimeBy(AlarmTimer.DEFAULT_TIMER_DELAY_MILLI)
        runCurrent()
        assertThat(timer.isFinished).isTrue()
        assertThat(actionData).isEqualTo(1)
    }

    @Test
    fun timer_finished_cantStart() = runTest {
        val timer = AlarmTimer(this){}
        timer.start()
        advanceTimeBy(AlarmTimer.DEFAULT_TIMER_DELAY_MILLI)
        runCurrent()
        assertThat(timer.isFinished).isTrue()
        assertThat(timer.start()).isFalse()
    }

    @Test
    fun timer_cantStartTwice() = runTest {
        val timer = AlarmTimer(this){}
        val startedOnce = timer.start()
        val startedTwice = timer.start()
        assertThat(startedOnce).isTrue()
        assertThat(startedTwice).isFalse()
    }

    @Test
    fun timer_notFinished_canStop() = runTest {
        var actionData = 0
        val timer = AlarmTimer(this){
            actionData += 1
        }
        timer.start()
        val stopped = timer.stop()
        assertThat(stopped).isTrue()
        assertThat(actionData).isEqualTo(0)
    }

    @Test
    fun timer_finished_cantStop() = runTest {
        val timer = AlarmTimer(this){}
        timer.start()
        advanceTimeBy(AlarmTimer.DEFAULT_TIMER_DELAY_MILLI)
        runCurrent()
        val stopped = timer.stop()
        assertThat(stopped).isFalse()
    }

    @Test
    fun timerManager_canCreateTimer() = runTest {
        val manager = AlarmTimerManager()
        assertThat(manager.startTimer(this) {}).isTrue()
        assertThat(manager.timer).isNotNull()
    }

    @Test
    fun timerManager_cantCreateTwoTimers() = runTest {
        val manager = AlarmTimerManager()
        manager.startTimer(this) {}
        assertThat(manager.timer).isNotNull()
        assertThat(manager.startTimer(this) {}).isFalse()
    }

    @Test
    fun timerManager_finishesTimerAndSetsNull() = runTest {
        val manager = AlarmTimerManager()
        manager.startTimer(this) {}
        advanceTimeBy(AlarmTimer.DEFAULT_TIMER_DELAY_MILLI)
        runCurrent()
        assertThat(manager.timer).isNull()
    }

    @Test
    fun timerManager_finishes_canStartNewTimer() = runTest {
        val manager = AlarmTimerManager()
        manager.startTimer(this) {}
        advanceTimeBy(AlarmTimer.DEFAULT_TIMER_DELAY_MILLI)
        runCurrent()
        assertThat(manager.timer).isNull()
        assertThat(manager.startTimer(this){}).isTrue()
    }

    @Test
    fun timerManager_unsafeReset_timerWasStarted_returnsTrue() = runTest {
        val manager = AlarmTimerManager()
        manager.startTimer(this) {}
        assertThat(manager.unsafeReset()).isTrue()
        assertThat(manager.timer).isNull()
    }
}