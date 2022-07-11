package app.timetoremind.mobile.screens.reminder.overview
//
//import androidx.arch.core.executor.testing.InstantTaskExecutorRule
//import androidx.navigation.NavController
//import androidx.test.espresso.Espresso.onView
//import androidx.test.espresso.action.ViewActions.click
//import androidx.test.espresso.assertion.ViewAssertions.matches
//import androidx.test.espresso.contrib.RecyclerViewActions
//import androidx.test.espresso.matcher.ViewMatchers.*
//import androidx.test.filters.MediumTest
//import app.timetoremind.mobile.R
//import app.timetoremind.mobile.data.ReminderRepository
//import app.timetoremind.mobile.data.domain.Reminder
//import app.timetoremind.mobile.util.launchFragmentInHiltContainer
//import app.timetoremind.sharedtest.MainCoroutineRule
//import dagger.hilt.android.testing.HiltAndroidRule
//import dagger.hilt.android.testing.HiltAndroidTest
//import kotlinx.coroutines.ExperimentalCoroutinesApi
//import kotlinx.coroutines.runBlocking
//import org.junit.Before
//import org.junit.Rule
//import org.junit.Test
//import org.mockito.Mockito.mock
//import org.mockito.Mockito.verify
//import javax.inject.Inject

/**
 * TODO:
 * Currently can't test due to use of conditional navigation requiring a NavController in
 * onCreate. (?)
 *
 * Try moving conditional navigation to onViewCreated so lifecycle can be observed?
 */
//@ExperimentalCoroutinesApi
//@HiltAndroidTest
//@MediumTest
//class RemindersFragmentTest {
//
//    @get:Rule
//    var hiltRule = HiltAndroidRule(this)
//
//    @get:Rule
//    val mainCoroutineRule = MainCoroutineRule()
//
//    @get:Rule
//    val instantExecutorRule = InstantTaskExecutorRule()
//
//    @Inject
//    lateinit var repository: ReminderRepository
//
//    private val reminders =
//        (1..3).map { Reminder(description = "My Reminder $it") }
//
//    @Before
//    fun setup() {
//        hiltRule.inject()
//        runBlocking { reminders.forEach { repository.save(it) } }
//    }
//
//    @Test
//    fun fragment_launches() {
//        val navController = mock(NavController::class.java)
//        launchFragmentInHiltContainer<RemindersFragment>(navController = navController)
//    }
//
//    @Test
//    fun recycler_displaysLocalReminders() {
//        val navController = mock(NavController::class.java)
//        launchFragmentInHiltContainer<RemindersFragment>(navController = navController)
//        reminders.forEach { reminder ->
//            onView(withText(reminder.description)).check(
//                matches(isDisplayed())
//            )
//        }
//    }
//
//    @Test
//    fun clickRecyclerItem_navigatesToEditor() {
//        val navController = mock(NavController::class.java)
//        launchFragmentInHiltContainer<RemindersFragment>(navController = navController)
//
//        val reminder = reminders[0]
//
//        // Click item with reminder.description and reminder.id
//        onView(withId(R.id.recycler_reminders_list)).perform(
//            RecyclerViewActions.actionOnItem<ReminderListAdapter.ReminderViewHolder>(
//                hasDescendant(withText(reminder.description)),
//                click()
//            )
//        )
//
//        // Verify the navigation happened with that reminder
//        verify(navController).navigate(
//            RemindersFragmentDirections.actionFragmentRemindersToReminderEditorActivity(
//                reminder.id
//            )
//        )
//    }
//}