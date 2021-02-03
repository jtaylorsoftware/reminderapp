package com.github.jeremyt135.reminderapp.screens.reminder.editor

import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.launchFragmentInHiltContainer
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import javax.inject.Inject

/**
 * TODO: when network data source is implemented, use @UninstallModules here
 * to replace it with a fake or mock.
 *
 * This test is mainly concerned about testing fragment->local persistence and
 * that buttons work as expected (by mocking Navigation). It should only access local persistence to
 * keep it fast.
 */
@HiltAndroidTest
@MediumTest
class ReminderEditorFragmentTest {

    /**
     * TODO: whe network data source is implemented, create the test module
     * for the fake or mock network data source here
     */

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
    fun withNoId_displaysEmptyReminder() {
        val args = bundleOf(
            Pair("reminderId", 0L)
        )
        launchFragmentInHiltContainer<ReminderEditorFragment>(args)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hintString = context.getString(R.string.editor_descriptionEdit_hint)
        onView(withId(R.id.edit_editor_description)).check(matches(withHint(hintString)))

        onView(withId(R.id.switch_editor_skip)).check(matches(isNotChecked()))
        onView(withId(R.id.switch_editor_vibrate)).check(matches(isNotChecked()))
    }

    @Test
    fun withReminderId_displaysAssociatedReminder() {
        val id = 1

        val args = bundleOf(
            Pair("reminderId", id.toLong())
        )
        launchFragmentInHiltContainer<ReminderEditorFragment>(args)

        val reminder = reminders[id - 1]

        onView(withText(reminder.description)).check(matches(isDisplayed()))

        // Verify switches by programmatically determining the correct matcher based on
        // the reminder value
        val getCheckMatcher = { value: Boolean ->
            when(value){
                true -> ::isChecked
                false  -> ::isNotChecked
            }
        }
        onView(withId(R.id.switch_editor_skip)).check(matches(getCheckMatcher(reminder.canSkip)()))
        onView(withId(R.id.switch_editor_vibrate)).check(matches(getCheckMatcher(reminder.vibrate)()))
    }

    @Test
    fun cancel_navigatesUpWithoutSaving() = runBlocking {
        val initialReminders = repository.getAll().first()

        // Open with an empty Reminder by passing 0L
        val args = bundleOf(
            Pair("reminderId", 0L)
        )
        val navController = mock(NavController::class.java)
        launchFragmentInHiltContainer<ReminderEditorFragment>(args) {
            Navigation.setViewNavController(requireView(), navController)
        }

        // Cancel the edits
        onView(withId(R.id.button_editor_cancel)).perform(click())

        verify(navController).navigateUp()

        // Assert that the size hasn't changed during this test
        val finalReminders = repository.getAll().first()
        assertThat(finalReminders).hasSize(initialReminders.size)
    }

    @Test
    fun save_navigatesUp() {
        val args = bundleOf(
            Pair("reminderId", 0L)
        )
        val navController = mock(NavController::class.java)
        launchFragmentInHiltContainer<ReminderEditorFragment>(args) {
            Navigation.setViewNavController(requireView(), navController)
        }

        onView(withId(R.id.button_editor_save)).perform(click())

        // Verify navigation happened
        verify(navController).navigateUp()
    }

    @Test
    fun save_savesReminderEdits() {
        // Launch fragment with reminders[0] with id = 1
        val id = 1
        val args = bundleOf(
            Pair("reminderId", id.toLong())
        )

        launchFragmentInHiltContainer<ReminderEditorFragment>(args){
            Navigation.setViewNavController(requireView(), mock(NavController::class.java))
        }


        // Create a new reminder that edits fields of current repo version
        val reminder = reminders[id - 1]
        val editedReminder = Reminder(
            reminder.description + "edited",
            reminder.status,
            canSkip = true,
            vibrate = true,
            id = reminder.id
        )

        // Apply edits
        onView(withId(R.id.edit_editor_description)).perform(replaceText(editedReminder.description))
        onView(withId(R.id.switch_editor_skip)).perform(click())
        onView(withId(R.id.switch_editor_vibrate)).perform(click())

        // Save changes
        onView(withId(R.id.button_editor_save)).perform(click())

        // Verify save happened
        lateinit var savedReminder: Reminder
        runBlocking {
            savedReminder = repository.findById(id.toLong())!!
        }
        assertThat(savedReminder).isEqualTo(editedReminder)
    }
}