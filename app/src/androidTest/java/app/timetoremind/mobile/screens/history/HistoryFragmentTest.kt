package app.timetoremind.mobile.screens.history

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.MediumTest
import app.timetoremind.mobile.data.HistoryRepository
import app.timetoremind.mobile.data.domain.History
import app.timetoremind.mobile.util.launchFragmentInHiltContainer
import app.timetoremind.mobile.screens.reminder.history.HistoryFragment
import app.timetoremind.mobile.util.uuid
import app.timetoremind.sharedtest.MainCoroutineRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@ExperimentalCoroutinesApi
@MediumTest
@HiltAndroidTest
class HistoryFragmentTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

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