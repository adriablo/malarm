package com.malarm

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {

    private fun format(totalMinutes: Long): String {
        val controller = Robolectric.buildActivity(MainActivity::class.java, Intent()).setup()
        return controller.get().formatTimeUntil(totalMinutes)
    }

    @Test
    fun timeUntilUnderAnHourShowsMinutes() {
        assertEquals("45 min", format(45))
        assertEquals("5 min", format(5))
        assertEquals("1 min", format(1))
    }

    @Test
    fun timeUntilHoursShowsHoursAndMinutes() {
        assertEquals("8 h 30 min", format(8 * 60 + 30))
        assertEquals("1 h 0 min", format(60))
    }

    @Test
    fun timeUntilDaysShowsDaysAndHours() {
        assertEquals("5 d 12 h", format((5 * 24 + 12) * 60))
        assertEquals("1 d 0 h", format(24 * 60))
    }

    @Test
    fun timeUntilWeeksShowsWeeksAndDays() {
        assertEquals("2 w 3 d", format(17 * 24 * 60))
        assertEquals("1 w 0 d", format(7 * 24 * 60))
    }
}
