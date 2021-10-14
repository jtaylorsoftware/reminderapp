package app.timetoremind.mobile.screens.preferences.about

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.timetoremind.mobile.R
import timber.log.Timber

class AboutFragment : Fragment(R.layout.fragment_about) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setVersionCodeText(view)
    }

    private fun setVersionCodeText(view: View) {
        val versionName = try {
            val packageInfo = requireContext().packageManager.getPackageInfo(
                requireContext().packageName, 0
            )
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e(e)
            ""
        }

        view.findViewById<TextView>(R.id.text_about_version_caption)?.text =
            getString(R.string.time_to_remind_version, versionName)

        view.findViewById<LinearLayout>(R.id.layout_about_legal)?.setOnClickListener {
            findNavController().navigate(R.id.fragment_legal)
        }
    }
}