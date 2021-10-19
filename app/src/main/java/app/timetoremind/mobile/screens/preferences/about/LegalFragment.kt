package app.timetoremind.mobile.screens.preferences.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import app.timetoremind.mobile.R
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity

class LegalFragment : Fragment(R.layout.fragment_legal) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTermsClick(view)
        setupPrivacyClick(view)
        setupLicensesClick(view)
    }

    private fun setupTermsClick(view: View) {
        val termsView = view.findViewById<View>(R.id.layout_legal_terms)
        val termsLink = getString(R.string.terms_link)

        termsView.setOnClickListener {
            val uri = Uri.parse(termsLink)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
    }

    private fun setupPrivacyClick(view: View) {
        val privacyView = view.findViewById<View>(R.id.layout_legal_privacy)
        val privacyLink = getString(R.string.privacy_link)

        privacyView.setOnClickListener {
            val uri = Uri.parse(privacyLink)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
    }

    private fun setupLicensesClick(view: View) {
        val licensesView = view.findViewById<View>(R.id.layout_legal_licenses)

        licensesView.setOnClickListener {
            startActivity(Intent(requireContext(), OssLicensesMenuActivity::class.java))
        }
    }
}