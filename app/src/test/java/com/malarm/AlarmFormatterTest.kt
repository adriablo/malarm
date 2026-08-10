package com.malarm

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmFormatterTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val original = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun oneShotIsOnce() {
        withLocale(Locale.US) {
            assertEquals("Once", AlarmFormatter.repeat(context, Alarm(1, 8, 0)))
        }
    }

    @Test
    fun allDaysIsDaily() {
        withLocale(Locale.US) {
            val alarm = Alarm(1, 8, 0, repeatDays = AlarmFormatter.DAY_NAMES.toSet())
            assertEquals("Daily", AlarmFormatter.repeat(context, alarm))
        }
    }

    @Test
    fun monToFriIsWeekdays() {
        withLocale(Locale.US) {
            val alarm = Alarm(1, 8, 0, repeatDays = AlarmFormatter.DAY_NAMES.take(5).toSet())
            assertEquals("Weekdays", AlarmFormatter.repeat(context, alarm))
        }
    }

    @Test
    fun satAndSunIsWeekends() {
        withLocale(Locale.US) {
            val alarm = Alarm(1, 8, 0, repeatDays = setOf(Calendar.SATURDAY, Calendar.SUNDAY))
            assertEquals("Weekends", AlarmFormatter.repeat(context, alarm))
        }
    }

    @Test
    fun specificDaysAreJoinedInOrder() {
        withLocale(Locale.US) {
            val alarm = Alarm(
                1, 8, 0,
                repeatDays = setOf(Calendar.SATURDAY, Calendar.MONDAY, Calendar.WEDNESDAY),
            )
            assertEquals("Mon, Wed, Sat", AlarmFormatter.repeat(context, alarm))
        }
    }

    @Test
    fun monthlyShowsDay() {
        withLocale(Locale.US) {
            assertEquals("Monthly on day 12", AlarmFormatter.repeat(context, Alarm(1, 8, 0, monthlyDay = 12)))
            assertEquals("Monthly on day 21", AlarmFormatter.repeat(context, Alarm(1, 8, 0, monthlyDay = 21)))
            assertEquals("Monthly on day 2", AlarmFormatter.repeat(context, Alarm(1, 8, 0, monthlyDay = 2)))
            assertEquals("Monthly on day 3", AlarmFormatter.repeat(context, Alarm(1, 8, 0, monthlyDay = 3)))
            assertEquals("Monthly on day 13", AlarmFormatter.repeat(context, Alarm(1, 8, 0, monthlyDay = 13)))
        }
    }

    @Test
    fun dateAlarmShowsItsDate() {
        val millis = 1786518000000L
        val expected = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
        assertEquals(expected, AlarmFormatter.repeat(context, Alarm(1, 8, 0, dateMillis = millis)))
    }
}
