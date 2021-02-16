package com.github.jeremyt135.reminderapp.screens.alarm

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.LargeTest
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER_ID
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.local.AlarmDao
import com.github.jeremyt135.reminderapp.data.local.AppDatabase
import com.github.jeremyt135.reminderapp.data.local.ReminderDao
import com.github.jeremyt135.reminderapp.util.toEpochMilli
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import javax.inject.Inject

@LargeTest
@HiltAndroidTest
class AlarmActivityTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var database: AppDatabase

    val time: LocalDateTime = LocalDateTime.now().plusHours(2)
    val reminder = Reminder(id = 1, time = time.toLocalTime(), description = "My Reminder")
    val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = time.toEpochMilli())

    @Before
    fun setup() {
        hiltRule.inject()

        runBlocking {
            reminderRepository.save(reminder)
            alarmRepository.save(alarm)
        }
    }

    @After
    fun teardown() {
        runBlocking {
            database.alarmDao().clearTable()
            database.historyDao().clearTable()
            database.reminderDao().clearTable()
        }
    }

    @Test
    fun activity_withReminderIdExtra_displaysAlarm() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, AlarmActivity::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminder.id)
        }
        ActivityScenario.launch<AlarmActivity>(intent)

        onView(withText(reminder.description)).check(matches(isDisplayed()))
    }
}