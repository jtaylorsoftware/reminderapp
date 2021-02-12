package com.github.jeremyt135.reminderapp.screens.reminder.editor

import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.PickerActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.github.jeremyt135.R
import com.github.jeremyt135.reminderapp.data.AlarmRepository
import com.github.jeremyt135.reminderapp.data.FakeAlarmRepository
import com.github.jeremyt135.reminderapp.data.FakeReminderRepository
import com.github.jeremyt135.reminderapp.data.ReminderRepository
import com.github.jeremyt135.reminderapp.data.domain.Reminder
import com.github.jeremyt135.reminderapp.data.domain.ReminderStatus
import com.github.jeremyt135.reminderapp.data.local.Alarm
import com.github.jeremyt135.reminderapp.di.DatabaseModule
import com.github.jeremyt135.reminderapp.di.RepositoryModule
import com.github.jeremyt135.reminderapp.launchFragmentInHiltContainer
import com.github.jeremyt135.reminderapp.util.withTime
import com.google.common.truth.Truth.assertThat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TODO: when network data source is implemented add to uninstall
 */
@UninstallModules(DatabaseModule::class, RepositoryModule::class)
@HiltAndroidTest
@MediumTest
class ReminderEditorFragmentTest {

    /**
     * TODO: when network data source is implemented, add fake here
     */
    @InstallIn(SingletonComponent::class)
    @Module
    object TestModule {
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
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    private val reminders =
        listOf(
            Reminder(id = 1),
            Reminder(
                "My Reminder", ReminderStatus.PENDING,
                daysOfWeek = sortedSetOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
                vibrate = true,
                id = 2
            ),
            Reminder(
                id = 3,
                time = LocalTime.now().plusHours(1).plusMinutes(10),
            ),
        )

    @Before
    fun setup() {
        hiltRule.inject()

        runBlocking { reminders.forEach { reminderRepository.save(it) } }
    }

    @Test
    fun withNoId_displaysEmptyReminder() {
        val args = bundleOf(
            Pair("reminderId", 0L)
        )
        val time = LocalTime.now()
        launchFragmentInHiltContainer<ReminderEditorFragment>(args)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hintString = context.getString(R.string.editor_descriptionEdit_hint)
        onView(withId(R.id.edit_editor_description)).check(matches(withHint(hintString)))
        onView(withId(R.id.timePicker_editor)).check(matches(withTime(time)))
        onView(withId(R.id.switch_editor_vibrate)).check(matches(isNotChecked()))
    }

    @Test
    fun withReminderId_displaysAssociatedReminder() {
        val reminder = reminders[1]
        val id = reminder.id

        val args = bundleOf(
            Pair("reminderId", id)
        )
        launchFragmentInHiltContainer<ReminderEditorFragment>(args)

        onView(withText(reminder.description)).check(matches(isDisplayed()))
        onView(withId(R.id.switch_editor_vibrate)).check(matches(isChecked()))
        onView(withId(R.id.timePicker_editor)).check(
            matches(withTime(LocalTime.of(reminder.hour, reminder.minute)))
        )

        onView(withId(R.id.toggle_editor_monday)).check(matches(isChecked()))
        onView(withId(R.id.toggle_editor_tuesday)).check(matches(isChecked()))
    }

    @Test
    fun cancel_navigatesUpWithoutSaving() = runBlocking {
        val initialReminders = reminderRepository.getAll().first()

        // Open with an empty Reminder by passing 0L
        val args = bundleOf(
            Pair("reminderId", 0L)
        )
        val navController = mock(NavController::class.java)
        launchFragmentInHiltContainer<ReminderEditorFragment>(args) {
            Navigation.setViewNavController(requireView(), navController)
        }

        // Cancel the edits
        onView(withId(R.id.button_editor_cancel)).perform(click())

        verify(navController).navigateUp()

        // Assert that the size hasn't changed during this test
        val finalReminders = reminderRepository.getAll().first()
        assertThat(finalReminders).hasSize(initialReminders.size)
    }

    @Test
    fun save_navigatesUp() {
        val args = bundleOf(
            Pair("reminderId", 3L)
        )
        val navController = mock(NavController::class.java)

        launchFragmentInHiltContainer<ReminderEditorFragment>(args) {
            Navigation.setViewNavController(requireView(), navController)
        }

        onView(withId(R.id.button_editor_save)).perform(click())

        // Verify navigation happened
        verify(navController).navigateUp()
    }

    @Test
    fun save_savesReminderEdits() {
        // Load reminder with defaults
        val reminder = reminders[0]
        val id = reminder.id
        val args = bundleOf(
            Pair("reminderId", id)
        )

        launchFragmentInHiltContainer<ReminderEditorFragment>(args) {
            Navigation.setViewNavController(requireView(), mock(NavController::class.java))
        }


        // Create a new reminder that edits fields of current repo version
        val editedReminder = Reminder(
            reminder.description + "edited",
            reminder.status,
            time = LocalTime.of(reminder.hour, reminder.minute).plusHours(12),
            daysOfWeek = sortedSetOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            vibrate = true,
            id = reminder.id
        )

        // Apply edits
        onView(withId(R.id.edit_editor_description)).perform(replaceText(editedReminder.description))
        onView(withId(R.id.switch_editor_vibrate)).perform(click())
        onView(withId(R.id.toggle_editor_monday)).perform(click())
        onView(withId(R.id.toggle_editor_tuesday)).perform(click())
        onView(withId(R.id.timePicker_editor)).perform(
            PickerActions.setTime(editedReminder.hour, editedReminder.minute)
        )

        // Save changes
        onView(withId(R.id.button_editor_save)).perform(click())

        // Verify save happened
        lateinit var savedReminder: Reminder
        runBlocking {
            savedReminder = reminderRepository.findById(id)!!
        }
        assertThat(savedReminder).isEqualTo(editedReminder)
    }

    @Test
    fun deleteButton_notEditing_isNotVisible(){
        val args = bundleOf(
            Pair("reminderId", 0L)
        )

        launchFragmentInHiltContainer<ReminderEditorFragment>(args) {
            Navigation.setViewNavController(requireView(), mock(NavController::class.java))
        }

        onView(withText("Delete")).check(matches(not(isDisplayed())))
    }

    @Test
    fun deleteButton_editing_deletesReminderAndAlarm() = runBlocking {
        val reminder = Reminder(id=reminderRepository.save(Reminder()))
        val alarm = Alarm(id=1,reminderId = reminder.id, triggerAt = 0)
        alarmRepository.save(alarm)

        val args = bundleOf(
            Pair("reminderId", reminder.id)
        )

        launchFragmentInHiltContainer<ReminderEditorFragment>(args) {
            Navigation.setViewNavController(requireView(), mock(NavController::class.java))
        }

        onView(withId(R.id.button_editor_delete)).check(matches(isDisplayed()))
        onView(withId(R.id.button_editor_delete)).perform(click())

        assertThat(reminderRepository.findById(reminder.id)).isNull()
        assertThat(alarmRepository.findById(reminder.id)).isNull()
    }
}