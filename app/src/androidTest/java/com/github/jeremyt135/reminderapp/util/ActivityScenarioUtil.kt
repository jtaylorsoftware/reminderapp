package com.github.jeremyt135.reminderapp.util

import androidx.test.core.app.ActivityScenario

/**
 * Launches an ActivityScenario, executes a lambda with the Scenario as its receiver, and lastly closes
 * the scenario
 */
inline fun <A : android.app.Activity> withActivityScenario(
    activityClass: Class<A>,
    body: ActivityScenario<A>.() -> Unit = {}
) {
    val scenario = ActivityScenario.launch(activityClass)
    body.invoke(scenario)
    scenario.close()
}