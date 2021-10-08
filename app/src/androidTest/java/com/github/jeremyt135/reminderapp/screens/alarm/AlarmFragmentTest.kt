package com.github.jeremyt135.reminderapp.screens.alarm

import android.content.Context
import android.content.Intent
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.MediumTest
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER_ID
import com.github.jeremyt135.reminderapp.data.*
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.History
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.SchedulingRules
import com.github.jeremyt135.reminderapp.data.local.FakeHistoryLocalDataSource
import com.github.jeremyt135.reminderapp.di.DatabaseModule
import com.github.jeremyt135.reminderapp.di.RepositoryModule
import com.github.jeremyt135.reminderapp.service.AlarmTimer
import com.github.jeremyt135.reminderapp.service.AlarmTimerManager
import com.github.jeremyt135.reminderapp.util.EventFlow
import com.github.jeremyt135.reminderapp.util.MutableEventFlow
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import com.github.jeremyt135.reminderapp.util.getShortLocalTimeDisplay
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@MediumTest
@HiltAndroidTest
@ExperimentalCoroutinesApi
class AlarmFragmentTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var historyRepository: HistoryRepository

    @Inject
    lateinit var timerManager: AlarmTimerManager

    val time: LocalDateTime = currentLocalDateTime().plusHours(2)
    val reminder = Reminder(
        schedulingRules = SchedulingRules(time = time.toLocalTime()),
        description = "My Reminder"
    )
    val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = time)

    /**
     *  Need to launch an Activity class to get the activityViewModel and
     *  to pass the reminderId
     */
    private fun launchAlarmFragmentWithReminder(reminderId: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(
            context,
            // Use an empty
            AlarmTestActivity::class.java
        ).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }

        ActivityScenario.launch<AlarmTestActivity>(intent).onActivity { activity ->
            activity.supportFragmentManager.commit {
                add<AlarmFragment>(android.R.id.content) // attach to root element of view
            }
        }
    }

    @Before
    fun setup() {
        hiltRule.inject()
        coEvery { timerManager.startTimer(any(), any(), any()) } returns true
        val mockTimer = mockkClass(AlarmTimer::class)
        coEvery { timerManager.timer } returns mockTimer
        every { mockTimer.stop() } returns true
        val eventFlow = MutableEventFlow<Unit>(null)
        every { mockTimer.timerFinished } returns eventFlow
        runBlocking {
            reminderRepository.save(reminder)
            alarmRepository.save(alarm)
        }
    }

    @Test
    fun displaysReminderData() {
        launchAlarmFragmentWithReminder(reminder.id)

        onView(withText(reminder.description)).check(matches(isDisplayed()))

        val time =
            LocalDate.now().atTime(reminder.schedulingRules.hour, reminder.schedulingRules.minute)
        onView(withText(reminder.description)).check(matches(isDisplayed()))
        onView(withText(time.getShortLocalTimeDisplay())).check(
            matches(isDisplayed())
        )
    }
}
