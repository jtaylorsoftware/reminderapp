package com.github.jeremyt135.reminderapp.util

import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * An immutable StateFlow-based Event wrapper that can only be set once, but can be observed by any number
 * of subscribers.
 */
open class EventFlow<T>(initialValue: T? = null) {
    protected val _flow = MutableStateFlow(EventImpl(initialValue))
    val flow: Flow<Event<T>> = _flow

    /**
     * value, peek, and handled are convenience for the holder of an EventFlow to check
     * if they want to collect or not
     */

    /**
     * Get the data for the event. Returns null if it has been handled (like Event would).
     */
    open val value: T?
        get() = _flow.value.value

    /**
     * Peek current value without receiving.
     */
    fun peek() = _flow.value.peek()

    val handled = _flow.value.handled

    /**
     * Collects the Event, but only performs action if the Event is not handled.
     */
    suspend inline fun onEvent(crossinline action: suspend (T?) -> Unit) =
        this.flow.collect { event -> event.value?.let { action(it) } }

    fun asLiveData(context: CoroutineContext = EmptyCoroutineContext) =
        this.flow.asLiveData(context)

    interface Event<T> {
        val handled: Boolean
        val value: T?
        fun peek(): T?
    }

    protected inner class EventImpl<T>(initialValue: T? = null, handled: Boolean = false) :
        Event<T> {
        private var _value = AtomicReference(initialValue)

        private val _handled = AtomicBoolean(handled)
        override val handled = _handled.get()

        override val value: T?
            get() = if (_handled.get()) {
                null
            } else {
                _handled.set(true)
                _value.get()
            }

        override fun peek() = _value.get()
    }
}

/**
 * A mutable version of SingleEvent where value can be set directly if the Event hasn't been handled.
 */
class MutableEventFlow<T>(initialValue: T? = null) :
    EventFlow<T>(initialValue) {

    override var value: T?
        get() = super.value
        set(value) {
            if (!super.handled) {
                _flow.value = EventImpl(value)
            }
        }
}

fun <T> MutableEventFlow<T>.asSingleEvent() = this as EventFlow<T>