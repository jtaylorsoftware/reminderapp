package com.github.jeremyt135.reminderapp.data.domain.parcel

import android.os.Parcel
import com.github.jeremyt135.reminderapp.data.domain.SchedulingRules
import com.github.jeremyt135.reminderapp.data.domain.SchedulingType
import com.github.jeremyt135.reminderapp.data.domain.parcel.DayOfWeekSetParceler.write
import kotlinx.parcelize.Parceler
import java.time.LocalDate
import java.time.LocalTime


object SchedulingRulesParceler : Parceler<SchedulingRules> {
    override fun create(parcel: Parcel): SchedulingRules = SchedulingRules(
        parcel.readParcelable(SchedulingType::class.java.classLoader)!!,
        parcel.readSerializable() as LocalDate,
        parcel.readSerializable() as LocalDate,
        parcel.readSerializable() as LocalTime,
        DayOfWeekSetParceler.create(parcel)
    )

    override fun SchedulingRules.write(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(type, flags)
        parcel.writeSerializable(startDate)
        parcel.writeSerializable(endDate)
        parcel.writeSerializable(time)
        daysOfWeek.write(parcel, flags)
    }
}