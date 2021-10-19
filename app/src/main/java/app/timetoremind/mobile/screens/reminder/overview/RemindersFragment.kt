package app.timetoremind.mobile.screens.reminder.overview

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import app.timetoremind.mobile.BuildConfig
import app.timetoremind.mobile.R
import app.timetoremind.mobile.databinding.FragmentRemindersBinding
import app.timetoremind.mobile.screens.reminder.getLayoutSpanCount
import app.timetoremind.mobile.screens.reminder.overview.dialog.RemindersFilterDialogFragment
import app.timetoremind.mobile.screens.reminder.overview.dialog.RemindersSortDialogFragment
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
        val navController = findNavController()
        val currentBackStackEntry = navController.currentBackStackEntry!!
        val savedStateHandle = currentBackStackEntry.savedStateHandle
        savedStateHandle.getLiveData<Boolean>(acceptedTermsKey).observe(currentBackStackEntry,
            { acceptedEula ->
                if (!acceptedEula) {
                    navController.navigate(R.id.fragment_welcome)
                }
            })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_reminders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBinding(view)
        setupFab()

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val acceptedEula = preferences.getBoolean(acceptedTermsKey, false)
        if (!acceptedEula && !BuildConfig.DEBUG) {
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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.reminders_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
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
        else -> super.onOptionsItemSelected(item)
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
}