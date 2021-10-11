package app.timetoremind.mobile.screens.history

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.MediumTest
import app.timetoremind.mobile.data.*
import app.timetoremind.mobile.data.domain.History
import app.timetoremind.mobile.launchFragmentInHiltContainer
import app.timetoremind.mobile.screens.reminder.history.HistoryFragment
import app.timetoremind.mobile.util.uuid
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

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