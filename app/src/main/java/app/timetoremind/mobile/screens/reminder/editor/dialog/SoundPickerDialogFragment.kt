package app.timetoremind.mobile.screens.reminder.editor.dialog

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.launch
import androidx.activity.result.registerForActivityResult
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import app.timetoremind.mobile.R
import app.timetoremind.mobile.databinding.DialogSoundOptionsBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * Dialog body for choosing vibrate & sound options.
 */
class SoundPickerDialogFragment() :
    DialogFragment() {
    private lateinit var listener: DialogListener
    private lateinit var binding: DialogSoundOptionsBinding
    private var vibrate: Boolean = false
    private var soundUri: String = ""
    private val viewModel: SoundPickerViewModel by viewModels {
        SoundPickerViewModel.Factory(requireActivity().application, vibrate, soundUri)
    }

    private val getAlarmSound =
        registerForActivityResult(PickRingtone(), RingtoneManager.TYPE_ALL) { uri ->
            Timber.d("Finished ringtone activity: $uri")
            uri?.let {
                viewModel.setSoundUri(it)
            }
        }


    /**
     * Constructs a SoundPickerDialogFragment.
     * @param vibrate Previous or initial vibrate option
     * @param soundUri Previous or initial chosen sound uri as a String
     */
    constructor(vibrate: Boolean, soundUri: String) : this() {
        this.vibrate = vibrate
        this.soundUri = soundUri
    }

    /**
     * Listener interface for host activity to implement.
     */
    interface DialogListener {
        fun onVibrateOptionChanged(vibrate: Boolean)
        fun onSoundUriChanged(soundUri: String)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = LayoutInflater.from(context)
        binding = DialogSoundOptionsBinding.inflate(inflater)

        return builder.apply {
            setView(binding.root)
            setTitle(R.string.dialog_soundPicker_title)
            setPositiveButton(R.string.apply) { _, _ ->
                listener.onVibrateOptionChanged(viewModel.vibrate.value!!)
                listener.onSoundUriChanged(viewModel.soundUri.value)
            }
            setNegativeButton(R.string.cancel) { _, _ ->
                Timber.d("SoundPicker cancel pressed")
            }
        }.create()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        try {
            listener = context as DialogListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement DialogListener")
        }
    }

    override fun onStart() {
        super.onStart()
        setupBinding(binding)
    }

    private fun setupBinding(binding: DialogSoundOptionsBinding) {
        binding.apply {
            lifecycleOwner = activity
            model = viewModel

            // When the "sound" switch is toggled, open sound selector
            switchSoundPickerSound.setOnClickListener {
                getAlarmSound.launch()
            }
            switchSoundPickerVibrate.setOnClickListener {
                viewModel.toggleVibrate()
            }
        }
    }
}

class SoundPickerViewModel(application: Application, vibrate: Boolean, soundUri: String) :
    AndroidViewModel(application) {
    private val _vibrate = MutableStateFlow(vibrate)
    val vibrate = _vibrate.asLiveData()

    private val _soundUri = MutableStateFlow(soundUri)
    val soundUri = _soundUri.asStateFlow()

    val soundDisplayString = _soundUri.map {
        "Sound: ${getSoundTitle(it) ?: "None"}"
    }.asLiveData()

    val soundChecked = _soundUri.map {
        it.isNotEmpty()
    }.asLiveData()

    fun toggleVibrate() {
        _vibrate.value = !_vibrate.value
    }

    fun setSoundUri(uri: Uri) {
        val uriString = uri.toString()
        if (uriString == _soundUri.value) {
            return
        }
        _soundUri.value = uriString
    }

    private fun getSoundTitle(soundUri: String): String? = if (soundUri.isEmpty()) null else
        RingtoneManager.getRingtone(getApplication(), Uri.parse(soundUri))
            ?.getTitle(getApplication())

    class Factory(
        private val application: Application,
        private val previousVibrate: Boolean,
        private val previousSoundUri: String
    ) :
        ViewModelProvider.NewInstanceFactory() {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T =
            SoundPickerViewModel(application, previousVibrate, previousSoundUri) as T
    }
}

private class PickRingtone : ActivityResultContract<Int, Uri?>() {
    override fun createIntent(context: Context, ringtoneType: Int) =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, ringtoneType)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.EMPTY)
        }

    override fun parseResult(resultCode: Int, result: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) {
            Timber.e("Result was $resultCode")
            return null
        }
        return result?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ?.also {
                Timber.d("Result was $it")
            }
    }
}