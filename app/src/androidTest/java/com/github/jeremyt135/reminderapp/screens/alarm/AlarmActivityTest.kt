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
import com.github.jeremyt135.reminderapp.AndroidTestTimerModule
import com.github.jeremyt135.reminderapp.broadcast.EXTRA_REMINDER_ID
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Alarm
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.SchedulingRules
import com.github.jeremyt135.reminderapp.data.local.AppDatabase
import com.github.jeremyt135.reminderapp.di.AlarmTimerModule
import com.github.jeremyt135.reminderapp.service.AlarmTimerManager
import com.github.jeremyt135.reminderapp.util.currentLocalDateTime
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import javax.inject.Inject

@LargeTest
@HiltAndroidTest
@UninstallModules(AlarmTimerModule::class, AndroidTestTimerModule::class)
class AlarmActivityTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var database: AppDatabase

    @BindValue
    val timerManager = AlarmTimerManager()

    val time: LocalDateTime = currentLocalDateTime().plusHours(2)
    val reminder = Reminder(
        schedulingRules = SchedulingRules(time = time.toLocalTime()),
        description = "My Reminder"
    )
    val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = time)

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

    @ExperimentalCoroutinesApi
    @Test
    fun activity_withReminderIdExtra_displaysAlarm() = runBlockingTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, AlarmActivity::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminder.id)
        }

        timerManager.startTimer(this, delay = 3000) {}
        ActivityScenario.launch<AlarmActivity>(intent)

        onView(withText(reminder.description)).check(matches(isDisplayed()))
    }
}