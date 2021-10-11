package app.timetoremind.mobile.data.local

import com.google.common.truth.Truth.*
import org.junit.Test
import java.time.DayOfWeek

class ConvertersTest {
    @Test
    fun daysOfWeekFromString_parsesCommaSeparatedDays(){
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)
        val daysString = days.joinToString()

        val daysParsed = Converters().daysOfWeekFromString(daysString)
        assertThat(daysParsed).containsExactlyElementsIn(days)
    }

    @Test
    fun daysOfWeekFromString_emptyValueString_returnsEmptySet(){
        val daysString = ""

        val daysParsed = Converters().daysOfWeekFromString(daysString)
        assertThat(daysParsed).isEmpty()
    }
}