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

/**
 * TODO: when network data source is implemented add to uninstall
 */
@UninstallModules(DatabaseModule::class, RepositoryModule::class)
@MediumTest
@HiltAndroidTest
class HistoryFragmentTest {

    /**
     * TODO: when network data source is implemented, add fake here
     */
    @InstallIn(SingletonComponent::class)
    @Module
    object TestModule {
        private val historyCache = mutableListOf<History>()

        @Provides
        @Singleton
        fun provideHistoryRepository(): HistoryRepository = FakeHistoryRepository(
            FakeHistoryLocalDataSource(historyCache)
        )

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
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: HistoryRepository

    private val historyEntries = mutableListOf(
        History(reminderId = 1, description = "History Entry One"),
        History(reminderId = 0, description = "History Entry Two"),
        History(reminderId = 2, description = "History Entry Three")
    )

    @Before
    fun setup(){
        hiltRule.inject()
        runBlocking {
            repository.saveAll(historyEntries).forEachIndexed { i, id ->
                historyEntries[i] = historyEntries[i].copy(id=id)
            }
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