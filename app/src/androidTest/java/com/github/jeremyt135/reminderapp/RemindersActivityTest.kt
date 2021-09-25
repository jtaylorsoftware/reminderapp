package com.github.jeremyt135.reminderapp

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.local.AppDatabase
import com.github.jeremyt135.reminderapp.screens.reminder.overview.ReminderListAdapter
import com.github.jeremyt135.reminderapp.util.withActivityScenario
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime
import javax.inject.Inject

@HiltAndroidTest
@LargeTest
class RemindersActivityTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    lateinit var context: Context

    @Inject
    lateinit var reminderRepo: ReminderRepository

    @Inject
    lateinit var alarmRepo: AlarmRepository

    @Inject
    lateinit var database: AppDatabase

    private val reminders =
        (1..3).map { Reminder(description = "My Reminder $it") }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        hiltRule.inject()
    }

    @After
    fun teardown() {
        runBlocking {
            database.alarmDao().clearTable()
            database.historyDao().clearTable()
            database.reminderDao().clearTable()
        }

        NotificationManagerCompat.from(context).cancelAll()
    }

    @Test
    fun activity_launches() {
        withActivityScenario(RemindersActivity::class.java)
    }

    @Test
    fun activity_opensToRemindersList() {
        runBlocking { reminders.forEach { reminderRepo.save(it) } }

        val reminder = reminders[0]

        withActivityScenario(RemindersActivity::class.java) {
            onView(withText(reminder.description)).check(
                matches(isDisplayed())
            )
        }
    }

    @Test
    fun fab_NavigatesToEditorWithEmptyReminder() {
        withActivityScenario(RemindersActivity::class.java) {
            onView(withId(R.id.fab_reminders_add)).perform(click())

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val hintString = context.getString(R.string.editor_descriptionEdit_hint)
            onView(withId(R.id.textinput_editor_description)).check(matches(withHint(hintString)))

//            onView(withId(R.id.switch_soundPicker_vibrate)).check(matches(isNotChecked()))

//            onView(withId(R.id.timePicker_editor)).check(matches(withTime(time)))
        }
    }
//
//    @Test
//    fun openEditor_andSaveNewReminder_schedulesAlarm() {
//        val hours = 10
//        val minutes = 15
//        val description = "My New Reminder"
//
//        withActivityScenario(RemindersActivity::class.java) {
//            /**
//             * Open editor
//             */
//            onView(withId(R.id.fab_reminders_add)).perform(click())
//
//            /**
//             * Set reminder fields
//             */
//            onView(withId(R.id.timePicker_editor)).perform(setTime(hours, minutes))
//            onView(withId(R.id.edit_editor_description)).perform(replaceText(description))
//
//            onView(withId(R.id.toggle_editor_monday)).perform(click())
//            onView(withId(R.id.toggle_editor_wednesday)).perform(click())
//
//            onView(withId(R.id.switch_soundPicker_vibrate)).perform(click())
//
//            /**
//             * Save
//             */
//            onView(withId(R.id.button_editor_save)).perform(click())
//
//            /**
//             * Verify on Reminders overview that the Reminder is added
//             */
//            onView(withText(description)).check(matches(isDisplayed()))
//
//            val time = Reminder(
//                schedulingRules = SchedulingRules(
//                    time = LocalTime.of(
//                        hours,
//                        minutes
//                    )
//                )
//            ).getTimeDisplayString()
//            onView(withText(time)).check(matches(isDisplayed()))
//
//            onView(withTagValue(equalTo(R.drawable.ic_notification_pending))).check(
//                matches(isDisplayed())
//            )
//
//            onView(withId(R.id.text_item_reminder_monday))
//                .check(matches(isDisplayed()))
//                .check(matches(hasTextColor(R.color.color_primary)))
//            onView(withId(R.id.text_item_reminder_wednesday))
//                .check(matches(isDisplayed()))
//                .check(matches(hasTextColor(R.color.color_primary)))
//
//            /**
//             * Verify the alarm was scheduled
//             */
//            val reminder = runBlocking {
//                reminderRepo.getAll().first().first { it.description == description }
//            }
//            val alarm = runBlocking {
//                alarmRepo.findByReminderId(reminder.id)
//            }
//            assertThat(alarm).isNotNull()
//
////            runBlocking { assertThat(alarmRepo.getFirstScheduledAlarm()).isNotNull() }
////            val alarmIntent = getCreatedIntentForAlarmStart(context, reminder.id)
////            assertThat(alarmIntent).isNotNull()
////            alarmIntent?.cancel()
//        }
//    }
//

    @Test
    fun tapReminder_navigatesToEditor_canDelete() {
        val reminder = reminders[0]

        runBlocking {
            reminderRepo.save(reminder)
        }

        withActivityScenario(RemindersActivity::class.java) {
            lateinit var navController: NavController
            onActivity {
                navController = it.findNavController(R.id.nav_host_fragment_main)
            }


            onView(withId(R.id.recycler_reminders_list)).perform(
                RecyclerViewActions.actionOnItem<ReminderListAdapter.ReminderViewHolder>(
                    hasDescendant(withText(reminder.description)),
                    click()
                )
            )

            /**
             * Verify editor has opened
             */
            onView(withId(R.id.textinput_editor_description))
                .check(matches(isDisplayed()))
                .check(
                    matches(
                        withText(reminder.description)
                    )
                )

            /**
             * Press delete
             */
            onView(withId(R.id.button_editor_delete)).check(matches(isDisplayed()))
            onView(withId(R.id.button_editor_delete)).perform(scrollTo(), click())
            onView(withText("OK")).inRoot(RootMatchers.isDialog()).check(matches(isDisplayed()))
                .perform(click())

            /**
             * Verify back on reminders list
             */
            assertThat(navController.currentDestination?.id).isEqualTo(R.id.fragment_reminders)

            /**
             * Verify that reminder has been deleted
             */
            runBlocking {
                assertThat(reminderRepo.findById(reminder.id)).isNull()
            }
        }
    }

}
