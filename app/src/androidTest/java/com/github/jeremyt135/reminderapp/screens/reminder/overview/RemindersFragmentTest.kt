package com.github.jeremyt135.reminderapp.screens.reminder.overview

import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.filters.MediumTest
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.data.*
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.local.FakeHistoryLocalDataSource
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
        private val historyCache = mutableListOf<History>()

        @Provides
        @Singleton
        fun provideReminderRepository(): ReminderRepository =
            FakeReminderRepository(reminderCache, alarmCache, historyCache)


        @Provides
        @Singleton
        fun provideAlarmRepository(): AlarmRepository = FakeAlarmRepository(alarmCache)


        @Provides
        @Singleton
        fun provideHistoryRepository(): HistoryRepository = FakeHistoryRepository(
            FakeHistoryLocalDataSource(historyCache)
        )
    }


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