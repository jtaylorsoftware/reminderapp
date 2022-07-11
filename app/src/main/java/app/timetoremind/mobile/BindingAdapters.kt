package app.timetoremind.mobile

import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import app.timetoremind.mobile.data.domain.History
import app.timetoremind.mobile.data.domain.ReminderAndHistory
import app.timetoremind.mobile.data.domain.ReminderStatus
import app.timetoremind.mobile.screens.reminder.alarm.AlarmListAdapter
import app.timetoremind.mobile.screens.reminder.calendar.CalendarAdapter
import app.timetoremind.mobile.screens.reminder.calendar.CalendarReminderListAdapter
import app.timetoremind.mobile.screens.reminder.calendar.ReminderAndColor
import app.timetoremind.mobile.screens.reminder.history.HistoryListAdapter
import app.timetoremind.mobile.screens.reminder.overview.ReminderListAdapter

@BindingAdapter("reminderListData")
fun bindReminderRecyclerView(recyclerView: RecyclerView, data: List<ReminderAndHistory>?) {
    (recyclerView.adapter as ReminderListAdapter?)?.submitList(data)
}

@BindingAdapter("reminderWithColorListData")
fun bindReminderRecyclerViewWithColor(recyclerView: RecyclerView, data: List<ReminderAndColor>?) {
    (recyclerView.adapter as CalendarReminderListAdapter?)?.submitList(data)
}

@BindingAdapter("historyListData")
fun bindHistoryRecyclerView(recyclerView: RecyclerView, data: List<History>?) {
    (recyclerView.adapter as HistoryListAdapter?)?.submitList(data)
}

@BindingAdapter("alarmListData")
fun bindAlarmRecyclerView(recyclerView: RecyclerView, data: List<Any>?) {
    (recyclerView.adapter as AlarmListAdapter?)?.submitList(data)
}

@BindingAdapter("calendarData")
fun bindCalendarRecyclerView(recyclerView: RecyclerView, data: List<Any>?) {
    (recyclerView.adapter as CalendarAdapter?)?.submitList(data)
}

@BindingAdapter("statusForIcon")
fun bindReminderStatusIcon(imageView: ImageView, history: History?) {
    val (resId, tint) = history?.let {
        when (it.completionStatus) {
            ReminderStatus.PENDING -> R.drawable.ic_notification_pending to R.color.status_pending
            ReminderStatus.SKIPPED -> R.drawable.ic_notification_skip to R.color.status_skip
            ReminderStatus.COMPLETED -> R.drawable.ic_notification_complete to R.color.status_complete
        }
    } ?: (R.drawable.ic_notification_pending to R.color.status_pending)
    imageView.setImageResource(resId)
    imageView.tag = resId
    ImageViewCompat.setImageTintList(
        imageView,
        ColorStateList.valueOf(ContextCompat.getColor(imageView.context, tint))
    )
}