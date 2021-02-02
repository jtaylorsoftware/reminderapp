package com.github.jeremyt135.reminderapp.screens.reminder.editor

import androidx.core.app.BundleCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.filters.MediumTest
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.data.domain.emptyReminder
import com.github.jeremyt135.reminderapp.launchFragmentInHiltContainer
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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
        (1..3).map { Reminder("My Reminder", ReminderStatus.PENDING, it.toLong()) }

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

        val reminder = emptyReminder()
        onView(withText("Reminder No. ${reminder.id}: ${reminder.description}")).check(
            matches(isDisplayed())
        )
    }

    @Test
    fun withReminderId_displaysAssociatedReminder() {
        val id = 1

        val args = bundleOf(
            Pair("reminderId", id.toLong())
        )
        launchFragmentInHiltContainer<ReminderEditorFragment>(args)

        val reminder = reminders[id - 1]
        onView(withText("Reminder No. ${reminder.id}: ${reminder.description}")).check(
            matches(isDisplayed())
        )
    }
}