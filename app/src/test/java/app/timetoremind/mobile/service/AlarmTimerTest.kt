package app.timetoremind.mobile.service

import app.timetoremind.mobile.MainCoroutineRule
import org.junit.Test
import com.google.common.truth.Truth.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Rule

@ExperimentalCoroutinesApi
class AlarmTimerTest {
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Test
    fun timer_finishes_performsAction() = mainCoroutineRule.runBlockingTest {
        var actionData = 0
        val timer = AlarmTimer(mainCoroutineRule){
            actionData += 1
        }
        timer.start()
        assertThat(timer.isStarted).isTrue()
        advanceTimeBy(AlarmTimer.DEFAULT_TIMER_DELAY_MILLI)
        assertThat(timer.isFinished).isTrue()
        assertThat(actionData).isEqualTo(1)
    }

    @Test
    fun timer_finished_cantStart() = mainCoroutineRule.runBlockingTest {
        val timer = AlarmTimer(mainCoroutineRule){}
        timer.start()
        advanceTimeBy(AlarmTimer.DEFAULT_TIMER_DELAY_MILLI)
        assertThat(timer.isFinished).isTrue()
        assertThat(timer.start()).isFalse()
    }

    @Test
    fun timer_cantStartTwice() = mainCoroutineRule.runBlockingTest {
        val timer = AlarmTimer(mainCoroutineRule){}
        val startedOnce = timer.start()
        val startedTwice = timer.start()
        assertThat(startedOnce).isTrue()
        assertThat(startedTwice).isFalse()
    }

    @Test
    fun timer_notFinished_canStop() = mainCoroutineRule.runBlockingTest {
        var actionData = 0
        val timer = AlarmTimer(mainCoroutineRule){
            actionData += 1
        }
        pauseDispatcher()
        timer.start()
        val stopped = timer.stop()
        resumeDispatcher()
        assertThat(stopped).isTrue()
        assertThat(actionData).isEqualTo(0)
    }

    @Test
    fun timer_finished_cantStop() = mainCoroutineRule.runBlockingTest {
        val timer = AlarmTimer(mainCoroutineRule){}
        timer.start()
        advanceTimeBy(AlarmTimer.DEFAULT_TIMER_DELAY_MILLI)
        val stopped = timer.stop()
        assertThat(stopped).isFalse()
    }

    @Test
    fun timerManager_canCreateTimer() = mainCoroutineRule.runBlockingTest {
        val manager = AlarmTimerManager()
        assertThat(manager.startTimer(mainCoroutineRule) {}).isTrue()
        pauseDispatcher()
        assertThat(manager.timer).isNotNull()
    }

    @Test
    fun timerManager_cantCreateTwoTimers() = mainCoroutineRule.runBlockingTest {
        val manager = AlarmTimerManager()
        manager.startTimer(mainCoroutineRule) {}
        pauseDispatcher()
        assertThat(manager.timer).isNotNull()
        assertThat(manager.startTimer(mainCoroutineRule) {}).isFalse()
    }

    @Test
    fun timerManager_finishesTimerAndSetsNull() = mainCoroutineRule.runBlockingTest {
        val manager = AlarmTimerManager()
        manager.startTimer(mainCoroutineRule) {}
        advanceTimeBy(AlarmTimer.DEFAULT_TIMER_DELAY_MILLI)
        assertThat(manager.timer).isNull()
    }

    @Test
    fun timerManager_finishes_canStartNewTimer() = mainCoroutineRule.runBlockingTest {
        val manager = AlarmTimerManager()
        manager.startTimer(mainCoroutineRule) {}
        advanceTimeBy(AlarmTimer.DEFAULT_TIMER_DELAY_MILLI)
        assertThat(manager.timer).isNull()
        assertThat(manager.startTimer(mainCoroutineRule){}).isTrue()
    }

    @Test
    fun timerManager_unsafeReset_timerWasStarted_returnsTrue() = mainCoroutineRule.runBlockingTest {
        val manager = AlarmTimerManager()
        pauseDispatcher()
        manager.startTimer(mainCoroutineRule) {}
        assertThat(manager.unsafeReset()).isTrue()
        assertThat(manager.timer).isNull()
    }
}