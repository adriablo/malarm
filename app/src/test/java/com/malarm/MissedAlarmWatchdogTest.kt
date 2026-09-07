package com.malarm

import android.app.AlarmManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MissedAlarmWatchdogTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("malarm", Context.MODE_PRIVATE).edit().clear().commit()
        kotlinx.coroutines.runBlocking { EventLog.clear(context) }
    }

    private fun awaitEvent(predicate: (AlarmEvent) -> Boolean): AlarmEvent? {
        // EventLog writes fan out on Dispatchers.IO; poll briefly.
        var events = kotlinx.coroutines.runBlocking { EventLog.getAll(context) }
        for (i in 0 until 50) {
            val hit = events.firstOrNull(predicate)
            if (hit != null) return hit
            Thread.sleep(20)
            events = kotlinx.coroutines.runBlocking { EventLog.getAll(context) }
        }
        return events.firstOrNull(predicate)
    }

    @Test
    fun missedLoggedWhenTriggerPassesWithoutFire() {
        val store = AlarmStore(context)
        store.save(Alarm(1, 8, 0, repeatDays = setOf(Calendar.MONDAY)))
        store.setExpectedTrigger(1, System.currentTimeMillis() - 10 * 60_000L)

        kotlinx.coroutines.runBlocking {
            MissedAlarmWatchdog.check(context, MissedAlarmWatchdog.snapshot(context))
        }

        val missed = awaitEvent { it.type == EventType.MISSED && it.alarmId == 1L }
        assertNotNull("expected MISSED for unfired alarm", missed)
    }

    @Test
    fun noMissedWhenAlarmFired() {
        val store = AlarmStore(context)
        store.save(Alarm(1, 8, 0, repeatDays = setOf(Calendar.MONDAY)))
        val expected = System.currentTimeMillis() - 10 * 60_000L
        store.setExpectedTrigger(1, expected)
        kotlinx.coroutines.runBlocking {
            EventLog.getDb(context).eventDao().insert(
                AlarmEvent(
                    timestamp = expected,
                    type = EventType.FIRED,
                    alarmId = 1,
                    label = "x",
                    details = "Alarm",
                ),
            )
        }
        awaitEvent { it.type == EventType.FIRED }

        kotlinx.coroutines.runBlocking {
            MissedAlarmWatchdog.check(context, MissedAlarmWatchdog.snapshot(context))
        }
        Thread.sleep(300)
        val missed = kotlinx.coroutines.runBlocking { EventLog.getAll(context) }
            .firstOrNull { it.type == EventType.MISSED }
        assertNull("no MISSED when the alarm fired", missed)
    }

    @Test
    fun staleExpectationDroppedForDisabledAlarm() {
        val store = AlarmStore(context)
        store.save(Alarm(1, 8, 0, repeatDays = setOf(Calendar.MONDAY), enabled = false))
        store.setExpectedTrigger(1, System.currentTimeMillis() - 10 * 60_000L)

        kotlinx.coroutines.runBlocking {
            MissedAlarmWatchdog.check(context, MissedAlarmWatchdog.snapshot(context))
        }
        Thread.sleep(300)
        val missed = kotlinx.coroutines.runBlocking { EventLog.getAll(context) }
            .firstOrNull { it.type == EventType.MISSED }
        assertNull("no MISSED for a disabled alarm", missed)
        assertEquals(emptyMap<Long, Long>(), store.expectedTriggers())
    }

    @Test
    fun scheduleRecordsAndCancelClearsExpectation() {
        val scheduler = AlarmScheduler(context)
        val alarm = Alarm(1, 8, 0, repeatDays = setOf(Calendar.MONDAY))
        scheduler.schedule(alarm)
        assertEquals(scheduler.nextTrigger(alarm), AlarmStore(context).expectedTriggers()[1L])
        scheduler.cancel(alarm, "test")
        assertEquals(emptyMap<Long, Long>(), AlarmStore(context).expectedTriggers())
    }

    @Test
    fun periodicRescheduleUsesTwoHourInterval() {
        AlarmScheduler(context).schedulePeriodicReschedule()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val periodic = shadowOf(alarmManager).scheduledAlarms.single {
            shadowOf(it.operation).savedIntent?.action == AlarmScheduler.ACTION_RESCHEDULE_ALL
        }
        assertEquals(2 * AlarmManager.INTERVAL_HOUR, periodic.intervalMs)
    }
}
