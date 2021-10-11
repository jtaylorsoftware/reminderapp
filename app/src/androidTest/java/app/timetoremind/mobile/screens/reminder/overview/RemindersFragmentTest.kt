package app.timetoremind.mobile.screens.reminder.overview

import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.filters.MediumTest
import app.timetoremind.mobile.R
import app.timetoremind.mobile.data.*
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.launchFragmentInHiltContainer
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import javax.inject.Inject

@HiltAndroidTest
@MediumTest
class RemindersFragmentTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: ReminderRepository

    private val reminders =
        (1..3).map { Reminder(description = "My Reminder $it") }

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking { reminders.forEach { repository.save(it) } }
    }

    @Test
    fun fragment_launches() {
        launchFragmentInHiltContainer<RemindersFragment>()
    }

    @Test
    fun recycler_displaysLocalReminders() {
        launchFragmentInHiltContainer<RemindersFragment>()
        reminders.forEach { reminder ->
            onView(withText(reminder.description)).check(
                matches(isDisplayed())
            )
        }
    }

    @Test
    fun clickRecyclerItem_navigatesToEditor() {
        val navController = mock(NavController::class.java)
        launchFragmentInHiltContainer<RemindersFragment>() {
            Navigation.setViewNavController(requireView(), navController)
        }

        val reminder = reminders[0]

        // Click item with reminder.description and reminder.id
        onView(withId(R.id.recycler_reminders_list)).perform(
            RecyclerViewActions.actionOnItem<ReminderListAdapter.ReminderViewHolder>(
                hasDescendant(withText(reminder.description)),
                click()
            )
        )

        // Verify the navigation happened with that reminder
        verify(navController).navigate(
            RemindersFragmentDirections.actionFragmentRemindersToReminderEditorActivity(
                reminder.id
            )
        )
    }
}