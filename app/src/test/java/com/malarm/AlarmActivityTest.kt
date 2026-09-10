package com.malarm

import android.app.AlarmManager
import android.content.Context
import android.os.Looper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmActivityTest {

    private lateinit var context: Context
    private lateinit var store: AlarmStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        store = AlarmStore(context)
        context.getSharedPreferences("malarm", Context.MODE_PRIVATE).edit().clear().commit()
        runBlocking { EventLog.clear(context) }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowOf(alarmManager).scheduledAlarms.forEach { alarmManager.cancel(it.operation!!) }
    }

    private val alarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun scheduledSnoozes() =
        shadowOf(alarmManager).scheduledAlarms.filter {
            val saved = shadowOf(it.operation).savedIntent
            saved?.action == AlarmScheduler.ACTION_ALARM &&
                saved.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
        }

    private fun awaitEvent(type: EventType): AlarmEvent {
        repeat(50) {
            val events = runBlocking { EventLog.getAll(context) }
            events.firstOrNull { it.type == type }?.let { return it }
            Thread.sleep(20)
        }
        throw AssertionError("event $type never appeared in the log")
    }

    private fun assertDefaultSnoozeArmed() {
        // Default snooze is 5 min (prefs cleared in setUp).
        val before = android.os.SystemClock.elapsedRealtime()
        val snooze = scheduledSnoozes().single()
        val after = android.os.SystemClock.elapsedRealtime()
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, snooze.type)
        assertTrue(snooze.triggerAtMs in before + 5 * 60_000L..after + 5 * 60_000L)
    }

    @Test
    fun backPressSnoozesAndFinishes() {
        store.save(Alarm(1, 8, 0, label = "Morning"))
        val controller = Robolectric.buildActivity(
            AlarmActivity::class.java,
            AlarmActivity.intent(context, 1L),
        ).setup()
        controller.get().onBackPressedDispatcher.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.get().isFinishing)
        assertDefaultSnoozeArmed()
        val event = awaitEvent(EventType.SNOOZED)
        assertEquals(1L, event.alarmId)
        assertEquals("Morning", event.label)
    }

    @Test
    fun missingAlarmFinishesImmediately() {
        val controller = Robolectric.buildActivity(
            AlarmActivity::class.java,
            AlarmActivity.intent(context, 999L),
        ).setup()
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun snoozeButtonArmsSnoozeAndFinishes() {
        // Manual 5.2 logic: full-screen Snooze arms the default snooze
        // directly (no broadcast round-trip) and logs SNOOZED.
        store.save(Alarm(1, 8, 0, label = "Morning"))
        val controller = Robolectric.buildActivity(
            AlarmActivity::class.java,
            AlarmActivity.intent(context, 1L),
        ).setup()
        controller.get().findViewById<android.widget.Button>(R.id.snooze).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.get().isFinishing)
        assertDefaultSnoozeArmed()
        val event = awaitEvent(EventType.SNOOZED)
        assertEquals("5 min", event.details)
    }

    @Test
    fun dismissButtonOnFiredOneShotStaysDisarmedAndFinishes() {
        // Manual 5.4 logic: full-screen Dismiss cancels directly and logs
        // DISMISSED with the alarm id + label. Post-fire one-shot state:
        // the receiver already disabled it, so nothing is re-armed.
        store.save(Alarm(1, 8, 0, label = "Morning", enabled = false))
        val controller = Robolectric.buildActivity(
            AlarmActivity::class.java,
            AlarmActivity.intent(context, 1L),
        ).setup()
        controller.get().findViewById<android.widget.Button>(R.id.dismiss).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.get().isFinishing)
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
        val event = awaitEvent(EventType.DISMISSED)
        assertEquals(1L, event.alarmId)
        assertEquals("Morning", event.label)
    }

    @Test
    fun dismissButtonRearmsRepeatingAlarmAndFinishes() {
        // Dismiss ends this instance, not the series: tomorrow's main
        // (armed at fire time) is cancelled by the dismiss and re-armed.
        store.save(Alarm(1, 8, 0, label = "Morning", repeatDays = setOf(java.util.Calendar.MONDAY)))
        AlarmScheduler(context).schedule(store.get(1)!!)
        val controller = Robolectric.buildActivity(
            AlarmActivity::class.java,
            AlarmActivity.intent(context, 1L),
        ).setup()
        controller.get().findViewById<android.widget.Button>(R.id.dismiss).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.get().isFinishing)
        val scheduled = shadowOf(alarmManager).scheduledAlarms
        assertEquals(1, scheduled.size)
        assertEquals(AlarmManager.RTC_WAKEUP, scheduled.single().type)
        val event = awaitEvent(EventType.DISMISSED)
        assertEquals(1L, event.alarmId)
        assertEquals("Morning", event.label)
    }

    @Test
    fun customButtonOpensSnoozePickerAndFinishes() {
        // Manual 5.3 logic: full-screen Custom opens the picker for this alarm.
        store.save(Alarm(1, 8, 0))
        val controller = Robolectric.buildActivity(
            AlarmActivity::class.java,
            AlarmActivity.intent(context, 1L),
        ).setup()
        controller.get().findViewById<android.widget.Button>(R.id.custom).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.get().isFinishing)
        val next = shadowOf(context as android.app.Application).nextStartedActivity
        assertNotNull("expected SnoozePickerActivity to start", next)
        assertEquals(
            SnoozePickerActivity::class.java.name,
            next!!.component?.className,
        )
        assertEquals(1L, next.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1))
    }
}
