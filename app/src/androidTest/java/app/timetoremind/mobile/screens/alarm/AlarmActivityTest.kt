package app.timetoremind.mobile.screens.alarm

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.LargeTest
import app.timetoremind.mobile.AndroidTestTimerModule
import app.timetoremind.mobile.broadcast.EXTRA_REMINDER_ID
import app.timetoremind.mobile.data.AlarmRepository
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.data.domain.Alarm
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.data.domain.SchedulingRules
import app.timetoremind.mobile.data.local.AppDatabase
import app.timetoremind.mobile.di.AlarmTimerModule
import app.timetoremind.mobile.service.AlarmTimerManager
import app.timetoremind.mobile.util.currentLocalDateTime
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