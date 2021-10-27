package app.timetoremind.mobile

import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.graphics.ColorUtils
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import app.timetoremind.mobile.broadcast.NOTIFICATION_LED_ARGB
import app.timetoremind.mobile.screens.reminder.overview.*
import app.timetoremind.mobile.screens.reminder.overview.dialog.RemindersFilterDialogFragment
import app.timetoremind.mobile.screens.reminder.overview.dialog.RemindersSortDialogFragment
import app.timetoremind.mobile.settings.SettingsActivity
import app.timetoremind.mobile.util.*
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : SettingsActivity(), RemindersSortDialogFragment.DialogListener,
    RemindersFilterDialogFragment.DialogListener {

    private val viewModel: RemindersViewModel by viewModels()

    private val bottomAppBar: BottomAppBar by lazy {
        requireNotNull(findViewById(R.id.bottom_app_bar))
    }
    private val bottomNavEmptyItem: View by lazy {
        requireNotNull(bottomAppBar.findViewById(R.id.bottomNav_item_empty))
    }
    private val remindersFab: FloatingActionButton by lazy {
        requireNotNull(findViewById(R.id.fab_reminders_add))
    }
    private val toolbar: Toolbar by lazy {
        requireNotNull(findViewById(R.id.top_app_bar))
    }
    private val bottomNavConstraintLayout: ConstraintLayout by lazy {
        requireNotNull(findViewById(R.id.bottomNav_constraintLayout))
    }

    private lateinit var bottomNavigationController: BottomNavigationController
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(toolbar)

        bottomNavigationController = createBottomNavigationController()
        val navController = setupNavController()
        bottomNavigationController.setUpWithNavController(navController)

        createNotificationChannels()

        onBackPressedDispatcher.addCallback(owner = this) {
            popToHome()
        }
    }

    private fun popToHome() {
        // Consider reminder list fragment as home
        val home = R.id.fragment_reminders
        // Pop up to home (reminders) fragment, popBackStack should return true
        // only if the previous fragment was not home
        val poppedToHome = navController.popBackStack(home, false)
        if (!poppedToHome) {
            // Returned false, meaning already on home, should finish activity
            finish()
        }
    }

    private fun createBottomNavigationController(): BottomNavigationController {
        return requireNotNull(findViewById<BottomAppBar>(R.id.bottom_app_bar)).run {
            navigationIcon = null
            setContentInsetsAbsolute(0, 0)

            val navButtons = listOf(
                BottomNavigationItem(
                    findViewById(R.id.bottomNav_item_calendar),
                    getString(R.string.calendar),
                    R.id.fragment_calendar
                ),
                BottomNavigationItem(
                    findViewById(R.id.bottomNav_item_reminders),
                    getString(R.string.reminders),
                    R.id.fragment_reminders
                ),
                BottomNavigationItem(
                    findViewById(R.id.bottomNav_item_alarms),
                    getString(R.string.alarms),
                    R.id.fragment_alarm_list
                ),
                BottomNavigationItem(
                    findViewById(R.id.bottomNav_item_history),
                    getString(R.string.history),
                    R.id.fragment_history
                )
            )
            BottomNavigationController(navButtons)
        }
    }

    private fun setupNavController(): NavController {
        val navHostFragment =
            requireNotNull(supportFragmentManager.findFragmentById(R.id.nav_host_fragment_main)) as NavHostFragment
        navController = navHostFragment.navController
        val appBarConfiguration =
            AppBarConfiguration(
                setOf(
                    R.id.fragment_reminders,
                    R.id.fragment_history,
                    R.id.fragment_calendar,
                    R.id.fragment_alarm_list
                )
            )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            Timber.d("Navigation destination is ${destination.label}")
            when (destination.id) {
                R.id.fragment_reminders -> {
                    toolbar.visibility = View.VISIBLE
                    showFab()
                }
                R.id.fragment_calendar -> {
                    toolbar.visibility = View.GONE
                    hideFab()
                }
                R.id.fragment_welcome -> {
                    toolbar.visibility = View.GONE
                    hideFab()
                    bottomAppBar.visibility = View.GONE
                }
                else -> {
                    toolbar.visibility = View.VISIBLE
                    hideFab()
                    bottomAppBar.visibility = View.VISIBLE
                }
            }
        }
        return navController
    }

    private fun showFab() {
        // Listen for the transition caused by adding the empty item
        bottomNavConstraintLayout.layoutTransition.addTransitionListener(object :
            LayoutTransition.TransitionListener {
            override fun startTransition(
                transition: LayoutTransition?,
                container: ViewGroup?,
                view: View?,
                transitionType: Int
            ) = Unit

            override fun endTransition(
                transition: LayoutTransition?,
                container: ViewGroup?,
                view: View?,
                transitionType: Int
            ) {
                if (bottomNavEmptyItem.visibility == View.VISIBLE && remindersFab.isOrWillBeHidden) {
                    // Show once the empty item is added
                    Timber.d("Showing FAB")
                    remindersFab.show()
                }
                bottomNavConstraintLayout.layoutTransition.removeTransitionListener(this)
            }
        })

        bottomAppBar.visibility = View.VISIBLE
        bottomNavEmptyItem.visibility = View.VISIBLE
        bottomAppBar.invalidate()
    }

    private fun hideFab() {
        if (remindersFab.isOrWillBeShown) {
            Timber.d("Hiding FAB")
            remindersFab.hide()
            bottomNavEmptyItem.visibility = View.GONE
            bottomAppBar.invalidate()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmChannel = NotificationChannel(
                getString(R.string.alarm_notification_channel_id),
                getString(R.string.alarm_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.alarm_notification_channel_description)

                vibrationPattern = null
                enableVibration(false)

                setSound(null, null)

                enableLights(true)
                lightColor = NOTIFICATION_LED_ARGB
            }
            val schedulingChannel = NotificationChannel(
                getString(R.string.scheduling_notification_channel_id),
                getString(R.string.scheduling_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.scheduling_notification_channel_description)
                vibrationPattern = null
                enableVibration(false)
                setSound(null, null)
                enableLights(false)
            }
            with((getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)) {
                createNotificationChannel(alarmChannel)
                createNotificationChannel(schedulingChannel)
            }
        }
    }

    override fun onSortRulesChange(sortRules: ReminderSortRules) {
        viewModel.changeSortRules(sortRules)
    }

    override fun onFilterRulesChange(filterRules: ReminderFilterRules) {
        viewModel.changeFilterRules(filterRules)
    }

    private data class BottomNavigationItem(
        val button: Button,
        val text: String,
        @IdRes val destination: Int
    )

    private inner class BottomNavigationController(items: List<BottomNavigationItem>) {
        private val destinations = mutableMapOf<Int, BottomNavigationItem>()

        private lateinit var navController: NavController

        private var currentItem: BottomNavigationItem? = null
            private set(value) {
                field?.deactivate()
                field = value
                field?.activate()
            }

        private val iconPaddingActive = resources.getDimension(R.dimen.bottomNav_iconPaddingActive)
        private val iconPadding = resources.getDimension(R.dimen.bottomNav_iconPadding)
        private val textColor =
            MaterialColors.getColor(this@MainActivity, R.attr.colorOnPrimary, Color.BLACK)

        init {
            Timber.d("textColor $textColor")
            items.forEach { navItem ->
                destinations[navItem.destination] = navItem
                navItem.button.apply {
                    setOnClickListener { navigateToDestination(navItem) }
                }
            }
        }

        fun setUpWithNavController(navController: NavController) {
            this.navController = navController
            navController.addOnDestinationChangedListener { _, destination, _ ->
                val newDestination = destinations[destination.id]
                if (currentItem != newDestination) {
                    currentItem = newDestination
                }
            }
        }

        private fun navigateToDestination(navItem: BottomNavigationItem) {
            if (currentItem == navItem) {
                return
            }
            currentItem = navItem
            navController.navigate(navItem.destination)
        }

        private fun BottomNavigationItem.activate() {
            button as MaterialButton
            button.setTextColor(textColor)
            button.text = text
            button.iconPadding = iconPaddingActive.toInt()
        }

        private fun BottomNavigationItem.deactivate() {
            button as MaterialButton
            ValueAnimator.ofFloat(iconPaddingActive, iconPadding).apply {
                duration = 300

                addUpdateListener { valueAnimator ->
                    button.iconPadding = (valueAnimator.animatedValue as Float).toInt()
                    button.setTextColor(
                        ColorUtils.setAlphaComponent(
                            textColor,
                            (255 * (1 - valueAnimator.animatedFraction)).toInt()
                        )
                    )
                }
                doOnEnd {
                    button.text = ""
                }
                start()
            }
        }
    }
}
