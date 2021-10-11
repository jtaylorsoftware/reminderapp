package app.timetoremind.mobile.data.domain.parcel

import android.os.Parcel
import app.timetoremind.mobile.data.local.Converters
import kotlinx.parcelize.Parceler
import java.time.DayOfWeek
import java.util.*


object DayOfWeekSetParceler : Parceler<SortedSet<DayOfWeek>> {
    override fun create(parcel: Parcel): SortedSet<DayOfWeek> =
        Converters().daysOfWeekFromString(parcel.readString()!!)

    override fun SortedSet<DayOfWeek>.write(parcel: Parcel, flags: Int) {
        parcel.writeString(Converters().daysOfWeekToString(this))
    }
}