package com.github.jeremyt135.reminderapp.util

import android.view.Menu
import android.view.MenuItem
import com.github.jeremyt135.R


/**
 * Settings item
 */
const val ITEM_SETTINGS = Menu.FIRST + 1

/**
 * Sets up a menu with a settings option
 */
fun prepareMenuForSettings(menu: Menu) {
    if (menu.findItem(ITEM_SETTINGS) == null) {
        menu.add(
            Menu.NONE,
            ITEM_SETTINGS,
            Menu.CATEGORY_SYSTEM,
            R.string.settings
        ).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
    }
}