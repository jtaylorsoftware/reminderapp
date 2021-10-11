package app.timetoremind.mobile.screens.preferences

import android.os.Bundle
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import app.timetoremind.mobile.R
import timber.log.Timber

class PreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate")
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Timber.d("onCreatePreferences")
        setPreferencesFromResource(R.xml.preference_screen, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val themeKey = getString(R.string.preference_key_theme)
        preferenceScreen.findPreference<ListPreference>(themeKey)?.let {
            val previousValue = preferenceManager.sharedPreferences.getString(themeKey, "SYSTEM")
            it.setValueIndex(it.findIndexOfValue(previousValue))
        }
    }
}