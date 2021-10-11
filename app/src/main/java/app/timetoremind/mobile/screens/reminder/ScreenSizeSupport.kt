package app.timetoremind.mobile.screens.reminder

import androidx.fragment.app.Fragment
import kotlin.math.floor

/**
 * The width threshold at which point the layout for lists will become grids.
 */
const val GRID_LAYOUT_THRESHOLD_WIDTH = 480

/**
 * The minimum width needed to display a reminder correctly in either orientation.
 */
const val MIN_ITEM_WIDTH = 200

/**
 * Calculates how many columns (spans) should exist in the layout. If value is 1 the
 * layout should be appearing as a single column list.
 * @return The number of columns (spans) to use
 */
fun Fragment.getLayoutSpanCount(): Int {
    val screenWidthDp = resources.configuration.screenWidthDp
    return if (screenWidthDp >= GRID_LAYOUT_THRESHOLD_WIDTH) {
        val minItemWidthDp = MIN_ITEM_WIDTH
        val maxFittedColumns = floor(screenWidthDp.toFloat() / minItemWidthDp).toInt()
        maxFittedColumns
    } else {
        1
    }
}