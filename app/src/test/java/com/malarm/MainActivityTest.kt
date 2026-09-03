package com.malarm

import android.content.Intent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
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

    private var originalTimeZone: java.util.TimeZone? = null

    @Before
    fun saveTimeZone() {
        originalTimeZone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() {
        java.util.TimeZone.setDefault(originalTimeZone)
    }

    private fun millis(month: Int, day: Int, hour: Int, minute: Int): Long =
        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            set(2026, month, day, hour, minute, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun evenHour(month: Int, day: Int, hour: Int, minute: Int): Pair<Int, Int> {
        val controller = Robolectric.buildActivity(MainActivity::class.java, Intent()).setup()
        return controller.get().nextEvenHour(millis(month, day, hour, minute))
    }

    @Test
    fun nextEvenHourRoundsUpToTopOfHour() {
        assertEquals(15 to 0, evenHour(java.util.Calendar.SEPTEMBER, 3, 14, 3))
        assertEquals(15 to 0, evenHour(java.util.Calendar.SEPTEMBER, 3, 14, 0))
    }

    @Test
    fun nextEvenHourKeepsExactHourFiveMinutesOut() {
        assertEquals(14 to 0, evenHour(java.util.Calendar.SEPTEMBER, 3, 13, 55))
    }

    @Test
    fun nextEvenHourRollsOverMidnight() {
        assertEquals(0 to 0, evenHour(java.util.Calendar.SEPTEMBER, 3, 23, 55))
        assertEquals(1 to 0, evenHour(java.util.Calendar.SEPTEMBER, 3, 23, 58))
    }

    @Test
    fun newAlarmDialogHasTitleAndSavePersistsAlarm() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, Intent()).setup()
        val activity = controller.get()
        val store = AlarmStore(activity)
        val before = store.all().size

        activity.findViewById<android.view.View>(R.id.fab).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val dialog = org.robolectric.shadows.ShadowDialog.getShownDialogs().lastOrNull()
        org.junit.Assert.assertNotNull("expected the alarm dialog", dialog)
        val title = dialog!!.findViewById<android.widget.TextView>(androidx.appcompat.R.id.alertTitle)
        assertEquals("New alarm", title.text.toString())

        dialog.findViewById<android.widget.Button>(R.id.dialog_save).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(before + 1, store.all().size)
    }

    @Test
    fun timeStepButtonsAdjustTimeBeforeSave() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, Intent()).setup()
        val activity = controller.get()
        val (hour, _) = activity.nextEvenHour(System.currentTimeMillis())
        val store = AlarmStore(activity)

        activity.findViewById<android.view.View>(R.id.fab).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val dialog = org.robolectric.shadows.ShadowDialog.getShownDialogs().lastOrNull()
        org.junit.Assert.assertNotNull("expected the alarm dialog", dialog)

        dialog!!.findViewById<android.widget.Button>(R.id.dialog_plus15).performClick()
        dialog.findViewById<android.widget.Button>(R.id.dialog_minus10).performClick()
        dialog.findViewById<android.widget.Button>(R.id.dialog_save).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        val saved = store.all().maxByOrNull { it.id }!!
        assertEquals(hour, saved.hour)
        assertEquals(5, saved.minute)
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
