package app.timetoremind.mobile.screens.reminder.editor

import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.fragment.app.add
import androidx.fragment.app.commitNow
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import app.timetoremind.mobile.R
import app.timetoremind.mobile.broadcast.EXTRA_REMINDER_ID
import app.timetoremind.mobile.data.AlarmRepository
import app.timetoremind.mobile.data.ReminderRepository
import app.timetoremind.mobile.data.domain.Alarm
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.data.domain.SchedulingRules
import app.timetoremind.mobile.util.currentLocalDateTime
import app.timetoremind.mobile.util.nestedScrollTo
import app.timetoremind.mobile.util.uuid
import app.timetoremind.sharedtest.MainCoroutineRule
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltAndroidTest
@MediumTest
@ExperimentalCoroutinesApi
class ReminderEditorFragmentTest {

    private fun launchEditorFragmentWithReminder(
        reminderId: String,
        action: ReminderEditorFragment.() -> Unit = {}
    ) {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            ReminderEditorTestActivity::class.java
        ).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }

        ActivityScenario.launch<ReminderEditorTestActivity>(intent).onActivity { activity ->
            activity.supportFragmentManager.commitNow {
                add<ReminderEditorFragment>(
                    android.R.id.content,
                    "testEditorFragment"
                )
            }

            val fragment =
                requireNotNull(activity.supportFragmentManager.findFragmentByTag("testEditorFragment"))
            (fragment as ReminderEditorFragment).action()
        }
    }

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    private lateinit var fragmentVM: ReminderEditorViewModel

    private val reminders =
        listOf(
            Reminder(description = uuid()),
            Reminder(
                description = "My Reminder",
                schedulingRules = SchedulingRules(
                    daysOfWeek = sortedSetOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY
                    )
                ),
                vibrate = true,
            ),
            Reminder(
                description = uuid(),
                schedulingRules = SchedulingRules(
                    time = LocalTime.now().plusHours(1).plusMinutes(10)
                ),
            ),
        )

    @Before
    fun setup() {
        hiltRule.inject()

        runBlocking { reminders.forEach { reminderRepository.save(it) } }
    }

    @Test
    fun withNoId_displaysEmptyReminder() {
        launchEditorFragmentWithReminder("")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hintString = context.getString(R.string.edit_reminder)
        onView(withId(R.id.textinput_editor_description)).check(matches(withHint(hintString)))
        onView(withId(R.id.textinput_editor_startDate)).check(matches(withHint(context.getString(R.string.editor_startDate_hint))))
//        onView(withId(R.id.textinput_editor_endDate)).check(matches(withHint(context.getString(R.string.editor_endDate_hint))))
        onView(withId(R.id.textinput_editor_time)).check(matches(withHint(context.getString(R.string.editor_time_hint))))
        onView(withId(R.id.textinput_editor_daysOfWeek)).check(matches(withHint(context.getString(R.string.editor_daysOfWeek_hint))))
        onView(withId(R.id.textinput_editor_snooze)).check(matches(withHint(context.getString(R.string.editor_snooze_hint))))
        onView(withId(R.id.textinput_editor_sound)).check(matches(withHint(context.getString(R.string.editor_sound_hint))))
    }

    @Test
    fun withReminderId_displaysAssociatedReminder() {
        val reminder = reminders[1].copy(
            schedulingRules = SchedulingRules(
                daysOfWeek = sortedSetOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY
                ), time = LocalTime.now(), startDate = LocalDate.now()
            ),
        )
        val id = reminder.id

        launchEditorFragmentWithReminder(id)

        onView(withText(reminder.description)).check(matches(isDisplayed()))
        onView(withId(R.id.textinput_editor_description)).check(matches(withText(reminder.description)))
        onView(withId(R.id.textinput_editor_startDate)).check(matches(withText("Today")))
        onView(withId(R.id.textinput_editor_time)).check(matches(withText(reminder.getTimeDisplayString())))
        onView(withId(R.id.textinput_editor_daysOfWeek)).check(matches(withText(reminder.getDaysDisplayString())))
        onView(withId(R.id.textinput_editor_snooze)).check(matches(withText(reminder.snoozeRules.getDisplayString())))
        onView(withId(R.id.textinput_editor_sound)).check(matches(withText("Vibrate, No sound")))
    }

    @ExperimentalCoroutinesApi
    @Test
    fun save_navigatesUp() {
        launchEditorFragmentWithReminder(reminders[2].id) {
            fragmentVM = this.viewModel
        }

        onView(withId(R.id.button_editor_save)).perform(click())
        assertThat(fragmentVM.navigateToReminders.value).isNotNull()
    }

    @Test
    fun save_savesReminderEdits() {
        // Load reminder with defaults
        val reminder = reminders[0]
        val id = reminder.id

        launchEditorFragmentWithReminder(id)

        // Create a new reminder that edits fields of current repo version
        val editedReminder = reminder.copy(
            description = reminder.description + "edited",
            schedulingRules = SchedulingRules(
//                time = LocalTime.of(reminder.schedulingRules.hour, reminder.schedulingRules.minute)
//                    .plusHours(12).plusMinutes(1),
//                daysOfWeek = sortedSetOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
            ),
//            vibrate = true,
        )

        // Apply edits
        onView(withId(R.id.textinput_editor_description)).perform(replaceText(editedReminder.description))
//        onView(withId(R.id.switch_soundPicker_vibrate)) .perform(scrollTo(), click())
//        onView(withId(R.id.toggle_editor_monday)) .perform(scrollTo(), click())
//        onView(withId(R.id.toggle_editor_tuesday)) .perform(scrollTo(), click())
//        onView(withId(R.id.timePicker_editor)).perform(
//            PickerActions.setTime(
//                editedReminder.schedulingRules.hour,
//                editedReminder.schedulingRules.minute
//            )
//        )

        // Save changes
        onView(withId(R.id.button_editor_save)).perform(click())

        // Verify save happened
        val savedReminder: Reminder = runBlocking {
            delay(1000)
            reminderRepository.findById(id)!!
        }
        assertThat(savedReminder).isEqualTo(editedReminder)
    }

    @Test
    fun deleteButton_notEditing_isNotVisible() {
        launchEditorFragmentWithReminder("")

        onView(withText("Delete")).check(matches(not(isDisplayed())))
    }

    @Test
    fun deleteButton_editing_deletesReminderAndAlarm() {
        val reminder = Reminder()
        runBlocking {
            reminderRepository.save(reminder)
            val alarm = Alarm(id = 1, reminderId = reminder.id, triggerAt = currentLocalDateTime())
            alarmRepository.save(alarm)
        }

        launchEditorFragmentWithReminder(reminder.id)

        onView(withId(R.id.button_editor_delete)).check(matches(isDisplayed()))
        onView(withId(R.id.button_editor_delete)).perform(nestedScrollTo(), click())
        onView(withText("OK")).inRoot(isDialog()).check(matches(isDisplayed())).perform(click())

        runBlocking {
            assertThat(reminderRepository.findById(reminder.id)).isNull()
            assertThat(alarmRepository.findByReminderId(reminder.id)).isNull()
        }
    }
}