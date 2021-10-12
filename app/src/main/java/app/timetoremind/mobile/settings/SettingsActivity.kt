package app.timetoremind.mobile.settings

import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import app.timetoremind.mobile.R
import app.timetoremind.mobile.screens.preferences.PreferencesActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * An Activity that uses a settings menu, and is aware of theme changes.
 */
@AndroidEntryPoint
open class SettingsActivity : AppCompatActivity() {
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        prepareMenuForSettings(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        ITEM_SETTINGS -> {
            startActivity(Intent(this, PreferencesActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /**
     * Sets up a menu with a settings option
     */
    private fun prepareMenuForSettings(menu: Menu) {
        if (menu.findItem(ITEM_SETTINGS) == null) {
            menu.add(
                Menu.NONE,
                ITEM_SETTINGS,
                Menu.CATEGORY_SYSTEM,
                R.string.settings
            ).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
    }
}

/**
 * Settings item
 */
const val ITEM_SETTINGS = Menu.FIRST + 1
