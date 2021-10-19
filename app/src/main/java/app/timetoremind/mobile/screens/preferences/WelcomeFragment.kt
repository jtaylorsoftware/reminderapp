package app.timetoremind.mobile.screens.preferences

import android.content.SharedPreferences
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import app.timetoremind.mobile.BuildConfig
import app.timetoremind.mobile.R

class WelcomeFragment : Fragment(R.layout.fragment_welcome) {
    private val acceptedTermsKey: String by lazy {
        getString(R.string.preference_key_accepted_terms)
    }

    private lateinit var preferences: SharedPreferences
    private lateinit var savedStateHandle: SavedStateHandle

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController()
        savedStateHandle = requireNotNull(navController.previousBackStackEntry).savedStateHandle
        savedStateHandle.set(acceptedTermsKey, false)

        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val acceptedEula = preferences.getBoolean(acceptedTermsKey, false)

        // Ensure this screen won't show up when user already accepted
        if (acceptedEula || BuildConfig.DEBUG) {
            navController.popBackStack()
        } else {
            setContentText(view)
            setupAcceptButton(view)
        }
    }

    private fun setContentText(view: View) {
        val content = view.findViewById<TextView>(R.id.text_welcome_content)
        val byUsing = getString(R.string.by_using)
        val terms = getString(R.string.terms_and_conditions)
        val privacy = getString(R.string.privacy_policy)
        val termsLink = getString(R.string.terms_link)
        val privacyLink = getString(R.string.privacy_link)

        val contentString = SpannableString(byUsing).apply {
            val termsIndex = byUsing.indexOf(terms)
            setSpan(
                URLSpan(termsLink),
                termsIndex,
                termsIndex + terms.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val privacyIndex = byUsing.indexOf(privacy)
            setSpan(
                URLSpan(privacyLink),
                privacyIndex,
                privacyIndex + privacy.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        content.text = contentString
        content.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setupAcceptButton(view: View) {
        val acceptButton = view.findViewById<Button>(R.id.button_welcome_accept)
        acceptButton.setOnClickListener {
            with(preferences.edit()) {
                putBoolean(acceptedTermsKey, true)
                apply()
            }
            savedStateHandle.set(acceptedTermsKey, true)
            findNavController().popBackStack()
        }
    }
}