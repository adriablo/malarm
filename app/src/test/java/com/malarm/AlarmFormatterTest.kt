package com.malarm

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmFormatterTest {

    @Test
    fun oneShotIsOnce() {
        assertEquals("Once", AlarmFormatter.repeat(Alarm(1, 8, 0)))
    }

    @Test
    fun allDaysIsDaily() {
        val alarm = Alarm(1, 8, 0, repeatDays = AlarmFormatter.DAY_NAMES.toSet())
        assertEquals("Daily", AlarmFormatter.repeat(alarm))
    }

    @Test
    fun monToFriIsWeekdays() {
        val alarm = Alarm(1, 8, 0, repeatDays = AlarmFormatter.DAY_NAMES.take(5).toSet())
        assertEquals("Weekdays", AlarmFormatter.repeat(alarm))
    }

    @Test
    fun satAndSunIsWeekends() {
        val alarm = Alarm(1, 8, 0, repeatDays = setOf(Calendar.SATURDAY, Calendar.SUNDAY))
        assertEquals("Weekends", AlarmFormatter.repeat(alarm))
    }

    @Test
    fun specificDaysAreJoinedInOrder() {
        val alarm = Alarm(
            1, 8, 0,
            repeatDays = setOf(Calendar.SATURDAY, Calendar.MONDAY, Calendar.WEDNESDAY),
        )
        assertEquals("Mon, Wed, Sat", AlarmFormatter.repeat(alarm))
    }

    @Test
    fun monthlyUsesOrdinalSuffix() {
        assertEquals("Monthly on 12th", AlarmFormatter.repeat(Alarm(1, 8, 0, monthlyDay = 12)))
        assertEquals("Monthly on 21st", AlarmFormatter.repeat(Alarm(1, 8, 0, monthlyDay = 21)))
        assertEquals("Monthly on 2nd", AlarmFormatter.repeat(Alarm(1, 8, 0, monthlyDay = 2)))
        assertEquals("Monthly on 3rd", AlarmFormatter.repeat(Alarm(1, 8, 0, monthlyDay = 3)))
        assertEquals("Monthly on 13th", AlarmFormatter.repeat(Alarm(1, 8, 0, monthlyDay = 13)))
    }

    @Test
    fun dateAlarmShowsItsDate() {
        val millis = 1786518000000L
        val expected = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
        assertEquals(expected, AlarmFormatter.repeat(Alarm(1, 8, 0, dateMillis = millis)))
    }
}
