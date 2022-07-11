package app.timetoremind.mobile.screens.reminder.overview

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import app.timetoremind.mobile.BuildConfig
import app.timetoremind.mobile.R
import app.timetoremind.mobile.databinding.FragmentRemindersBinding
import app.timetoremind.mobile.screens.reminder.getLayoutSpanCount
import app.timetoremind.mobile.screens.reminder.overview.dialog.RemindersFilterDialogFragment
import app.timetoremind.mobile.screens.reminder.overview.dialog.RemindersSortDialogFragment
import app.timetoremind.mobile.work.AlarmSchedulingWorker
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class RemindersFragment : Fragment() {
    private val viewModel: RemindersViewModel by activityViewModels()

    private lateinit var reminderListAdapter: ReminderListAdapter

    private val acceptedTermsKey: String by lazy {
        getString(R.string.preference_key_accepted_terms)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // https://developer.android.com/guide/navigation/navigation-conditional
        // Ensure user accepts EULA by using conditional navigation - observe changes to
        // a SavedStateHandle containing the ACCEPTED flag. On first launch, it will be false,
        // so user gets navigated to EULA screen. Otherwise, no navigation happens.
        val navController = findNavController()
        val currentBackStackEntry = navController.currentBackStackEntry!!
        val savedStateHandle = currentBackStackEntry.savedStateHandle
        savedStateHandle.getLiveData<Boolean>(acceptedTermsKey)
            .observe(currentBackStackEntry) { acceptedTerms ->
                Timber.d("User accepted terms: $acceptedTerms")
                if (!acceptedTerms) {
                    navController.navigate(R.id.fragment_welcome)
                } else {
                    // User has accepted terms
                    doFirstTimeSetup()
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reminders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBinding(view)
        setupFab()
        setupMenu()

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val acceptedTerms = preferences.getBoolean(acceptedTermsKey, false)
        if (!acceptedTerms && !BuildConfig.DEBUG) {
            // Make user accept terms
            findNavController().navigate(R.id.fragment_welcome)
        } else {
            viewModel.navigateToEditor.asLiveData().observe(viewLifecycleOwner) { event ->
                event.value?.let { reminder ->
                    Timber.d("navigateToEditor received reminder: $reminder")
                    findNavController().navigate(
                        RemindersFragmentDirections.actionFragmentRemindersToReminderEditorActivity(
                            reminder.id
                        )
                    )
                }
            }
        }
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.reminders_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
                R.id.item_reminders_sort -> {
                    val dialog = RemindersSortDialogFragment(viewModel.reminderSortRules)
                    dialog.show(childFragmentManager, "dialogReminderSort")
                    true
                }
                R.id.item_reminders_filter -> {
                    val dialog = RemindersFilterDialogFragment(viewModel.reminderFilterRules)
                    dialog.show(childFragmentManager, "dialogReminderFilter")
                    true
                }
                R.id.item_reminders_clear -> {
                    buildClearDialog()?.show()
                    true
                }
                else -> false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupBinding(view: View) {
        FragmentRemindersBinding.bind(view).apply {
            model = viewModel
            lifecycleOwner = viewLifecycleOwner

            val spanCount = getLayoutSpanCount()
            recyclerRemindersList.layoutManager = GridLayoutManager(requireContext(), spanCount)

            reminderListAdapter =
                ReminderListAdapter(spanCount, ReminderListAdapter.OnClickListener {
                    viewModel.editReminder(it.reminder)
                })
            recyclerRemindersList.adapter = reminderListAdapter
            registerForContextMenu(recyclerRemindersList)
        }
    }

    private fun setupFab() {
        activity?.findViewById<View>(R.id.fab_reminders_add)?.setOnClickListener {
            viewModel.addReminder()
        } ?: run {
            Timber.e("RemindersFragment: could not set up FAB, activity null.")
        }
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val inflater = MenuInflater(context)
        inflater.inflate(R.menu.reminders_context_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.item_reminders_delete -> {
            reminderListAdapter.selectedContextReminder?.let { viewModel.deleteReminder(it) }
            true
        }
        R.id.item_reminders_markComplete -> {
            reminderListAdapter.selectedContextReminder?.let { viewModel.completeReminder(it) }
            true
        }
        R.id.item_reminders_markSkipped -> {
            reminderListAdapter.selectedContextReminder?.let { viewModel.skipReminder(it) }
            true
        }
        R.id.item_reminders_edit -> {
            reminderListAdapter.selectedContextReminder?.let { viewModel.editReminder(it) }
            true
        }
        else -> super.onContextItemSelected(item)
    }

    private fun buildClearDialog(): AlertDialog? {
        return activity?.let {
            AlertDialog.Builder(it).apply {
                setMessage(R.string.dialog_clearReminders_message)
                setTitle(R.string.dialog_clearReminders_title)
                setPositiveButton(R.string.button_ok) { _, _ ->
                    Timber.d("User confirmed clear reminders")
                    viewModel.deleteAllReminders()
                }
                setNegativeButton(R.string.cancel) { _, _ ->
                    Timber.d("User cancelled clear reminders")
                }
            }.create()
        }
    }

    private fun doFirstTimeSetup() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val firstStartupKey = getString(R.string.preference_key_first_startup)
        val isFirstStartup = preferences.getBoolean(firstStartupKey, true)

        if (isFirstStartup) {
            Timber.d("Performing first time setup")

            with(preferences.edit()) {
                putBoolean(firstStartupKey, false)
                apply()
            }

            val scheduleInterval =
                preferences
                    .getInt(getString(R.string.preference_key_schedule_interval), 30)
                    .toLong()

            // Start unique periodic work request for the first time
            AlarmSchedulingWorker.enqueue(requireContext(), scheduleInterval)
        }
    }
}