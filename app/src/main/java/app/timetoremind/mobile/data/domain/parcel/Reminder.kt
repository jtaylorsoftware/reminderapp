package app.timetoremind.mobile.data.domain.parcel

import android.os.Parcel
import android.os.Parcelable
import app.timetoremind.mobile.data.domain.Reminder
import app.timetoremind.mobile.data.domain.SnoozeRules
import app.timetoremind.mobile.data.domain.parcel.SchedulingRulesParceler.write
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

@Parcelize
@TypeParceler<Reminder, ReminderParceler>
data class ReminderParcel(val data: Reminder): Parcelable

object ReminderParceler : Parceler<Reminder> {
    override fun create(parcel: Parcel): Reminder = Reminder(
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readByte().toInt() == 1,
        parcel.readString()!!,
        parcel.readParcelable(SnoozeRules::class.java.classLoader)!!,
        SchedulingRulesParceler.create(parcel)
    )

    override fun Reminder.write(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(description)
        parcel.writeByte(if (vibrate) 1 else 0)
        parcel.writeString(notifSoundUri)
        parcel.writeParcelable(snoozeRules, flags)
        schedulingRules.write(parcel, flags)
    }
}