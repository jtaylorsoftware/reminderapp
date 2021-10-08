package com.github.jeremyt135.reminderapp.screens.history

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.MediumTest
import com.github.jeremyt135.reminderapp.data.*
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.local.FakeHistoryLocalDataSource
import com.github.jeremyt135.reminderapp.di.DatabaseModule
import com.github.jeremyt135.reminderapp.di.RepositoryModule
import com.github.jeremyt135.reminderapp.launchFragmentInHiltContainer
import com.github.jeremyt135.reminderapp.screens.reminder.history.HistoryFragment
import com.github.jeremyt135.reminderapp.util.uuid
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
import javax.inject.Inject
import javax.inject.Singleton

@MediumTest
@HiltAndroidTest
class HistoryFragmentTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: HistoryRepository

    private val historyEntries = mutableListOf(
        History(reminderId = uuid(), description = "History Entry One"),
        History(reminderId = uuid(), description = "History Entry Two"),
        History(reminderId = uuid(), description = "History Entry Three")
    )

    @Before
    fun setup(){
        hiltRule.inject()
        runBlocking {
            repository.saveAll(historyEntries)
        }
    }

    @Test
    fun displaysAllHistoryEntries() {
        launchFragmentInHiltContainer<HistoryFragment>()

        historyEntries.forEach {
            onView(withText(it.description)).check(matches(isDisplayed()))
        }
    }
}