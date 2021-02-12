package com.github.jeremyt135.reminderapp.util

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Performs an action that should emit to a flow and asserts that it emitted.
 */
fun <T> verifyFlowEmitted(flow: SharedFlow<T>, coroutineScope: CoroutineScope, action: () -> Unit) {
    var eventEmitted = false
    coroutineScope.launch {
        flow.collect {
            eventEmitted = true
            cancel()
        }
    }
    // Do action that emits event
    action()
    assertThat(eventEmitted).isTrue()
}