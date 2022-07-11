package app.timetoremind.mobile.service

import app.timetoremind.mobile.util.EventFlow
import app.timetoremind.mobile.util.MutableEventFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/**
 * Manages creating an AlarmTimer instance that could be shared
 * between objects.
 */
class AlarmTimerManager {
    private val mutex = Mutex()
    private var _timer = AtomicReference<AlarmTimer?>(null)
    private var currentTimerJob: Job? = null

    /**
     * The current running timer. Holding a reference to this
     * timer could result in memory leaks, it's preferable
     * to always reference it through the AlarmTimerManager.
     */
    val timer: AlarmTimer?
        get() = _timer.get()

    /**
     * Initializes a new AlarmTimer with the given finish action.
     * Only one timer can exist, and if it has started this
     * method will not replace it.
     * @return True if created a new timer and started it.
     */
    suspend fun startTimer(
        coroutineScope: CoroutineScope,
        delay: Long = AlarmTimer.DEFAULT_TIMER_DELAY_MILLI,
        onFinish: suspend () -> Unit
    ): Boolean = mutex.withLock {
        if (timer != null && !timer!!.isFinished) {
            Timber.w("Client attempted to call startTimer when timer already started")
            return false
        }

        val newTimer = AlarmTimer(coroutineScope, onFinish)
        currentTimerJob = coroutineScope.launch {
            newTimer.timerFinished.flow.collect { finished ->
                finished.value?.let {
                    mutex.withLock {
                        Timber.d("AlarmTimerManager clearing AlarmTimer instance")
                        _timer.set(null)
                        currentTimerJob?.cancel()
                    }
                }
            }
        }
        _timer.set(newTimer)

        return timer!!.start(delay)
    }

    /**
     * Forcefully resets the AlarmTimer instance. The current
     * AlarmTimer will be stopped, and the timer instance is set
     * to null.
     * Called unsafe because this will disrupt clients that expect
     * the timer to end naturally.
     * @return True if there was a timer that was stopped
     */
    suspend fun unsafeReset(): Boolean = mutex.withLock {
        val stopped = timer?.stop() ?: false
        _timer.set(null)
        currentTimerJob?.cancel()
        return stopped
    }
}

/**
 * Runs an action after a set amount of time. The action
 * can only be set and completed once, but any number of observers can subscribe
 * to .timerFinished.
 */
class AlarmTimer(
    val coroutineScope: CoroutineScope,
    private var onFinish: suspend () -> Unit
) {
    private val _isStarted: AtomicBoolean = AtomicBoolean(false)
    val isStarted: Boolean
        get() = _isStarted.get()

    private val _isFinished: AtomicBoolean = AtomicBoolean(false)
    val isFinished: Boolean
        get() = _isFinished.get()

    private lateinit var job: Job

    private val _timerFinished = MutableEventFlow<Unit>()

    /**
     * Event fired after the timer finishes
     */
    val timerFinished: EventFlow<Unit> = _timerFinished

    /**
     * Starts timer.
     * @return True if timer was started
     */
    fun start(
        timeUntilSnoozeMilli: Long = DEFAULT_TIMER_DELAY_MILLI,
    ): Boolean {
        if (_isStarted.compareAndSet(false, true) && !isFinished) {
            Timber.d("AlarmTimer started")
            job = coroutineScope.launch {
                try {
                    delay(max(MIN_TIMER_DELAY_MILLI, timeUntilSnoozeMilli))
                } finally {
                    // Even if delay is stopped, always try to call onFinish
                    withContext(NonCancellable) {
                        if (_isFinished.compareAndSet(false, true)) {
                            onFinish()

                            _timerFinished.value = Unit
                            Timber.d("AlarmTimer finishing")
                        }
                    }
                }
            }
            return true
        }

        return false
    }

    /**
     * Stops the timer, preventing it from completing.
     * @return true if the timer was stopped or false if it already finished.
     */
    fun stop(): Boolean =
        if (_isFinished.compareAndSet(false, true)) {
            job.cancel()
            Timber.d("timer stop() called")
            true
        } else {
            false
        }

    companion object {
        /**
         * Minimum value for how long it should wait
         */
        const val MIN_TIMER_DELAY_MILLI = 30L * 1000 // 30 seconds

        /**
         * Default value for how long an Alarm should wait until auto-snoozing in milliseconds.
         */
        const val DEFAULT_TIMER_DELAY_MILLI: Long = MIN_TIMER_DELAY_MILLI
    }
}