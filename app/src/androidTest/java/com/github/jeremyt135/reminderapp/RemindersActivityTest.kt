package com.github.jeremyt135.reminderapp

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.util.withActivityScenario
import com.github.jeremyt135.reminderapp.util.withTime
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime
import javax.inject.Inject

@HiltAndroidTest
@LargeTest
class RemindersActivityTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: ReminderRepository

    private val reminders =
        (1..3).map { Reminder("My Reminder", ReminderStatus.PENDING, id = it.toLong()) }

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking { reminders.forEach { repository.save(it) } }
    }

    @Test
    fun activity_launches() {
        withActivityScenario(RemindersActivity::class.java)
    }

    @Test
    fun activity_opensToRemindersList() = runBlocking {
        val reminder = reminders[0]

        withActivityScenario(RemindersActivity::class.java) {
            onView(withText("Reminder No. ${reminder.id}: ${reminder.description}")).check(
                matches(isDisplayed())
            )
        }
    }

    @Test
    fun fab_NavigatesToEditorWithEmptyReminder() = runBlocking {
        withActivityScenario(RemindersActivity::class.java) {
            val time = LocalTime.now()
            onView(withId(R.id.fab_reminders_add)).perform(click())

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val hintString = context.getString(R.string.editor_descriptionEdit_hint)
            onView(withId(R.id.edit_editor_description)).check(matches(withHint(hintString)))

            onView(withId(R.id.switch_editor_vibrate)).check(matches(isNotChecked()))

            onView(withId(R.id.timePicker_editor)).check(matches(withTime(time)))
        }
    }
}
