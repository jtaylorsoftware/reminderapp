package com.github.jeremyt135.reminderapp.screens.reminder.overview

import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.filters.MediumTest
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.FakeAlarmRepository
import com.github.jeremyt135.reminderapp.data.FakeReminderRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.data.local.Alarm
import com.github.jeremyt135.reminderapp.di.DatabaseModule
import com.github.jeremyt135.reminderapp.di.RepositoryModule
import com.github.jeremyt135.reminderapp.launchFragmentInHiltContainer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TODO: when network data source is implemented add to uninstall
 */
@UninstallModules(DatabaseModule::class, RepositoryModule::class)
@HiltAndroidTest
@MediumTest
class RemindersFragmentTest {

    /**
     * TODO: when network data source is implemented, add fake here
     */
    @InstallIn(SingletonComponent::class)
    @Module
    object TestModule {
        private val reminderCache: MutableList<Reminder> = mutableListOf()
        private val alarmCache: MutableList<Alarm> = mutableListOf()

        @Provides
        @Singleton
        fun provideReminderRepository(): ReminderRepository =
            FakeReminderRepository(reminderCache, alarmCache)


        @Provides
        @Singleton
        fun provideAlarmRepository(): AlarmRepository = FakeAlarmRepository(alarmCache)
    }


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
    fun fragment_launches() {
        launchFragmentInHiltContainer<RemindersFragment>()
    }

    @Test
    fun recycler_displaysLocalReminders() {
        launchFragmentInHiltContainer<RemindersFragment>()
        reminders.forEach { reminder ->
            onView(withText("Reminder No. ${reminder.id}: ${reminder.description}")).check(
                matches(isDisplayed())
            )
        }
    }

    @Test
    fun fab_NavigatesToEditor() {
        val navController = mock(NavController::class.java)
        launchFragmentInHiltContainer<RemindersFragment>() {
            Navigation.setViewNavController(requireView(), navController)
        }

        onView(withId(R.id.fab_reminders_add)).perform(click())

        verify(navController).navigate(
            RemindersFragmentDirections.actionFragmentRemindersToFragmentReminderEditor(
                0L
            )
        )
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
                // TODO: This string will have to change later when item_reminder is updated
                hasDescendant(withText("Reminder No. ${reminder.id}: ${reminder.description}")),
                click()
            )
        )

        // Verify the navigation happened with that reminder
        verify(navController).navigate(
            RemindersFragmentDirections.actionFragmentRemindersToFragmentReminderEditor(
                reminder.id
            )
        )
    }
}