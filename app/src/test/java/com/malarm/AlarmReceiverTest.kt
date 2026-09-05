package com.malarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmReceiverTest {

    private lateinit var context: Context
    private lateinit var store: AlarmStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        store = AlarmStore(context)
        // Clear shared state so tests are independent.
        AlarmReceiver.resetForTest()
        runBlocking { EventLog.clear(context) }
        context.getSharedPreferences("malarm", Context.MODE_PRIVATE).edit().clear().commit()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowOf(alarmManager).scheduledAlarms.forEach { alarmManager.cancel(it.operation!!) }
    }

    private fun intentFor(alarmId: Long, snooze: Boolean = false): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_ALARM
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, snooze)
        }

    private fun receive(intent: Intent) {
        AlarmReceiver().onReceive(context, intent)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private val scheduledAlarms
        get() = shadowOf(
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager,
        ).scheduledAlarms

    private fun channelExists(): Boolean =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .getNotificationChannel(AlarmNotifier.CHANNEL_ID) != null

    @Test
    fun ignoresOtherActions() {
        receive(Intent(context, AlarmReceiver::class.java).apply { action = "other" })
        assertTrue(store.all().isEmpty())
        assertTrue(scheduledAlarms.isEmpty())
    }

    @Test
    fun unknownAlarmIdIsIgnored() {
        receive(intentFor(999))
        assertTrue(store.all().isEmpty())
        assertTrue(scheduledAlarms.isEmpty())
    }

    @Test
    fun oneShotAlarmIsDisabledAfterFiring() {
        store.save(Alarm(1, 8, 0))
        receive(intentFor(1))
        assertFalse(store.get(1)!!.enabled)
    }

    @Test
    fun repeatingAlarmIsRescheduled() {
        store.save(Alarm(1, 8, 0, repeatDays = setOf(Calendar.MONDAY)))
        receive(intentFor(1))
        assertTrue(store.get(1)!!.enabled)
        assertEquals(1, scheduledAlarms.size)
    }

    @Test
    fun disabledAlarmDoesNotRing() {
        store.save(Alarm(1, 8, 0, enabled = false))
        receive(intentFor(1))
        assertTrue(scheduledAlarms.isEmpty())
        assertFalse(channelExists())
    }

    @Test
    fun snoozeFiresEvenWhenAlarmIsDisabled() {
        store.save(Alarm(1, 8, 0, enabled = false))
        receive(intentFor(1, snooze = true))
        assertTrue(channelExists())
    }

    @Test
    fun duplicateDeliveryWithinWindowIsIgnored() {
        store.save(Alarm(1, 8, 0, repeatDays = setOf(Calendar.MONDAY)))
        receive(intentFor(1))
        receive(intentFor(1))
        // Let both async FIRED writes land, then assert only one survived.
        Thread.sleep(500)
        val fired = runBlocking { EventLog.getAll(context) }.count { it.type == EventType.FIRED }
        assertEquals(1, fired)
    }

    @Test
    fun snoozeVariantIsNotADuplicateOfMain() {
        store.save(Alarm(1, 8, 0, repeatDays = setOf(Calendar.MONDAY)))
        receive(intentFor(1))
        receive(intentFor(1, snooze = true))
        Thread.sleep(500)
        val fired = runBlocking { EventLog.getAll(context) }.count { it.type == EventType.FIRED }
        assertEquals(2, fired)
    }

    @Test
    fun dismissActionStopsRinging() {
        store.save(Alarm(1, 8, 0))
        receive(Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_DISMISS
        })
        assertTrue(scheduledAlarms.isEmpty())
        assertFalse(channelExists())
    }

    @Test
    fun dismissWithAlarmIdLogsTheAlarm() {
        store.save(Alarm(1, 8, 0, label = "Morning"))
        receive(Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_DISMISS
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, 1L)
        })
        val event = awaitEvent { it.type == EventType.DISMISSED }
        assertEquals(1L, event.alarmId)
        assertEquals("Morning", event.label)
    }

    @Test
    fun dismissWithoutAlarmIdLogsNullAlarm() {
        store.save(Alarm(1, 8, 0))
        receive(Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_DISMISS
        })
        val event = awaitEvent { it.type == EventType.DISMISSED }
        assertNull(event.alarmId)
    }

    // EventLog writes asynchronously on Dispatchers.IO; poll until the event
    // lands (bounded) instead of reading the DB immediately.
    private fun awaitEvent(predicate: (AlarmEvent) -> Boolean): AlarmEvent {
        repeat(50) {
            val events = runBlocking { EventLog.getAll(context) }
            events.firstOrNull(predicate)?.let { return it }
            Thread.sleep(20)
        }
        throw AssertionError("event never appeared in the log")
    }

    @Test
    fun dismissWithUnknownAlarmIdStillStopsRinging() {
        receive(Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_DISMISS
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, 999L)
        })
        assertTrue(scheduledAlarms.isEmpty())
        assertFalse(channelExists())
    }

    @Test
    fun dismissCancelsPendingSnooze() {
        store.save(Alarm(1, 8, 0))
        receive(Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_SNOOZE
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, 1L)
        })
        assertEquals(1, scheduledAlarms.size)
        receive(Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_DISMISS
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, 1L)
        })
        assertTrue(scheduledAlarms.isEmpty())
    }

    @Test
    fun snoozeUsesConfiguredDuration() {
        // Manual 9.6: Settings snooze duration drives the ACTION_SNOOZE delay.
        store.save(Alarm(1, 8, 0, label = "Morning"))
        store.setSnoozeMinutes(15)
        val before = SystemClock.elapsedRealtime()
        receive(Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_SNOOZE
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, 1L)
        })
        val after = SystemClock.elapsedRealtime()
        val snooze = scheduledAlarms.single()
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, snooze.type)
        assertTrue(
            "snooze trigger ${snooze.triggerAtMs} outside 15-min window",
            snooze.triggerAtMs in before + 15 * 60_000L..after + 15 * 60_000L,
        )
        val event = awaitEvent { it.type == EventType.SNOOZED }
        assertEquals(1L, event.alarmId)
        assertEquals("Morning", event.label)
        assertEquals("15 min", event.details)
    }

    @Test
    fun fireSnoozeDismissLogsFullCycle() {
        // Manual 10.1: FIRED, SNOOZED (minutes), DISMISSED (id + label).
        store.save(Alarm(1, 8, 0, label = "Morning", repeatDays = setOf(Calendar.MONDAY)))
        receive(intentFor(1))
        receive(Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_SNOOZE
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, 1L)
        })
        receive(Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_DISMISS
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, 1L)
        })
        // EventLog writes on Dispatchers.IO; poll until the full cycle lands.
        var events = runBlocking { EventLog.getAll(context) }
        for (i in 0 until 50) {
            val types = events.map { it.type }.toSet()
            if (types.containsAll(setOf(EventType.FIRED, EventType.SNOOZED, EventType.DISMISSED))) break
            Thread.sleep(20)
            events = runBlocking { EventLog.getAll(context) }
        }
        val fired = events.firstOrNull { it.type == EventType.FIRED }
        val snoozed = events.firstOrNull { it.type == EventType.SNOOZED }
        val dismissed = events.firstOrNull { it.type == EventType.DISMISSED }
        assertEquals(1L, fired?.alarmId)
        assertEquals("Morning", fired?.label)
        assertEquals("Morning", snoozed?.label)
        assertTrue(snoozed?.details?.endsWith("min") == true)
        assertEquals(1L, dismissed?.alarmId)
        assertEquals("Morning", dismissed?.label)
        assertTrue(scheduledAlarms.isEmpty())
    }

    @Test
    fun dateAlarmFiresOnceThenDisables() {
        // Code-review §1.3: the receiver treats a date alarm as a one-shot
        // (disable, no reschedule) even if stale repeat flags are present,
        // and the stored copy is stripped clean. (The store itself now
        // normalizes on save/parse, so the flags here are cleaned before
        // receive — this locks the fire-once behavior; see AlarmJsonTest
        // and AlarmStoreTest for the normalization layers.)
        store.save(
            Alarm(
                1, 8, 0, label = "Combo",
                repeatDays = setOf(Calendar.MONDAY),
                monthlyDay = 12,
                dateMillis = System.currentTimeMillis() + 24 * 60 * 60 * 1000L,
            ),
        )
        receive(intentFor(1))
        val stored = store.get(1)!!
        assertFalse(stored.enabled)
        assertTrue(stored.repeatDays.isEmpty())
        assertNull(stored.monthlyDay)
        assertNotNull(stored.dateMillis)
        assertTrue(scheduledAlarms.isEmpty())
    }

    private fun rescheduleAll() {
        receive(Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_RESCHEDULE_ALL
        })
    }

    private fun seedCalibration() {
        store.setClockCalibration(
            SystemClock.elapsedRealtime(),
            System.currentTimeMillis(),
        )
    }

    @Test
    fun rescheduleAllIsNoOpWhenTimezoneUnchanged() {
        store.save(Alarm(1, 8, 0, repeatDays = setOf(Calendar.MONDAY)))
        store.setTimeZoneId(TimeZone.getDefault().id)
        seedCalibration()
        rescheduleAll()
        assertTrue(scheduledAlarms.isEmpty())
    }

    @Test
    fun rescheduleAllReArmsAlarmsWhenTimezoneChanged() {
        store.save(Alarm(1, 8, 0, repeatDays = setOf(Calendar.MONDAY)))
        store.setTimeZoneId("not/current")
        seedCalibration()
        rescheduleAll()
        assertEquals(1, scheduledAlarms.size)
        assertEquals(TimeZone.getDefault().id, store.timeZoneId())
    }
}
