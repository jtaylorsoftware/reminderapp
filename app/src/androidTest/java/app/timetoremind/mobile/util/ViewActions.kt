package app.timetoremind.mobile.util

import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anyOf

// Fix for scrollTo not working with the CoordinatorLayout/NestedScrollView
// https://stackoverflow.com/questions/35272953/espresso-scrolling-not-working-when-nestedscrollview-or-recyclerview-is-in-coor
class NestedScrollToAction(
    private val original: androidx.test.espresso.action.ScrollToAction = androidx.test.espresso.action.ScrollToAction()
) : ViewAction by original {

    override fun getConstraints(): Matcher<View> = anyOf(
        allOf(
            withEffectiveVisibility(Visibility.VISIBLE),
            isDescendantOfA(isAssignableFrom(NestedScrollView::class.java))),
        original.constraints
    )
}

fun nestedScrollTo(): ViewAction = ViewActions.actionWithAssertions(NestedScrollToAction())