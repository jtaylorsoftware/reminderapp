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
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.local.Alarm
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@MediumTest
@HiltAndroidTest
class AlarmFragmentTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    val time: LocalDateTime = LocalDateTime.now().plusHours(2)
    val reminder = Reminder(id = 1, time = time.toLocalTime(), description = "My Reminder")
    val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = time.toEpochMilli())

    /**
     *  Need to launch an Activity class to get the activityViewModel and
     *  to pass the reminderId
     */
    private fun launchAlarmFragmentWithReminder(reminderId: Long) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(
            context,
            // Use an empty
            AlarmTestActivity::class.java
        ).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }

        ActivityScenario.launch<AlarmTestActivity>(intent).onActivity { activity ->
            activity.viewModel.loadReminderData(reminder.id)
            activity.supportFragmentManager.commit {
                add<AlarmFragment>(android.R.id.content) // attach to root element of view
            }
        }
    }

    @Before
    fun setup() {
        hiltRule.inject()

        runBlocking {
            reminderRepository.save(reminder)
            alarmRepository.save(alarm)
        }
    }

    @Test
    fun displaysReminderData() {
        launchAlarmFragmentWithReminder(reminder.id)

        onView(withText(reminder.description)).check(matches(isDisplayed()))

        val time = LocalDate.now().atTime(reminder.hour, reminder.minute)
        onView(withText(reminder.description)).check(matches(isDisplayed()))
        onView(withText(time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))).check(
            matches(isDisplayed())
        )
    }
}
