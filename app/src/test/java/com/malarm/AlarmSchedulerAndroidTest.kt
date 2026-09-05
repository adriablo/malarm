package com.malarm

import android.app.AlarmManager
import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmSchedulerAndroidTest {

    private lateinit var context: Context
    private lateinit var scheduler: AlarmScheduler
    private var priorCanScheduleExactAlarms: Boolean = false

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        scheduler = AlarmScheduler(context)
        // Read through the shadowed instance method (no public static getter
        // exists) so tearDown can restore the prior global value. The method
        // only exists on S+; pre-S always uses the exact path anyway.
        priorCanScheduleExactAlarms =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            } else {
                false
            }
    }

    @After
    fun tearDown() {
        // Restore instead of forcing false: this is global shadow state that
        // otherwise leaks into other test classes via sandbox reuse.
        ShadowAlarmManager.setCanScheduleExactAlarms(priorCanScheduleExactAlarms)
    }

    private val alarmManager get() =
        shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)

    private val repeating = Alarm(1, 8, 0, repeatDays = setOf(Calendar.MONDAY))

    @Suppress("DEPRECATION")
    private fun assertSchedulesMainAlarm(alarm: Alarm) {
        scheduler.schedule(alarm)
        val alarms = alarmManager.scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(
            AlarmScheduler.requestCode(alarm.id, AlarmScheduler.ROLE_MAIN),
            shadowOf(alarms[0].operation).requestCode,
        )
    }

    private fun tomorrowMidnight(): Calendar =
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    @Test
    fun scheduleCreatesSingleAlarmWithMainRequestCode() {
        assertSchedulesMainAlarm(repeating)
    }

    @Test
    fun scheduleDoesNothingForExpiredOneShot() {
        scheduler.schedule(
            Alarm(1, 0, 0, dateMillis = System.currentTimeMillis() - 60_000L),
        )
        assertTrue(alarmManager.scheduledAlarms.isEmpty())
    }

    @Test
    fun cancelRemovesScheduledAlarms() {
        scheduler.schedule(repeating)
        scheduler.cancel(repeating)
        assertTrue(alarmManager.scheduledAlarms.isEmpty())
    }

    @Test
    fun cancelCancelsPendingSnooze() {
        // Manual 6.4 (delete snoozed alarm): both main and snooze must go.
        scheduler.schedule(repeating)
        scheduler.scheduleSnooze(repeating, 60_000L)
        assertEquals(2, alarmManager.scheduledAlarms.size)
        scheduler.cancel(repeating)
        assertTrue(alarmManager.scheduledAlarms.isEmpty())
    }

    @Test
    fun editCancelsSnoozeAndReschedulesToNewTime() {
        // Manual 6.5 (edit snoozed alarm): old snooze is dropped, main re-arms
        // at the new time.
        scheduler.schedule(repeating)
        scheduler.scheduleSnooze(repeating, 60_000L)
        val updated = repeating.copy(hour = 10, minute = 30)
        scheduler.cancel(repeating)
        scheduler.schedule(updated)
        val remaining = alarmManager.scheduledAlarms
        assertEquals(1, remaining.size)
        assertEquals(
            AlarmScheduler.requestCode(updated.id, AlarmScheduler.ROLE_MAIN),
            shadowOf(remaining.single().operation).requestCode,
        )
        assertEquals(scheduler.nextTrigger(updated), remaining.single().triggerAtMs)
    }

    @Test
    fun editFlowPersistsAndReschedules() {
        // Manual 3.3 (edit time/repeat): store update + cancel + re-schedule.
        val store = AlarmStore(context)
        store.save(repeating)
        val updated = repeating.copy(hour = 10, minute = 30)
        store.save(updated)
        scheduler.cancel(repeating)
        scheduler.schedule(updated)
        assertEquals(updated, store.get(repeating.id))
        val remaining = alarmManager.scheduledAlarms
        assertEquals(1, remaining.size)
        assertEquals(scheduler.nextTrigger(updated), remaining.single().triggerAtMs)
    }

    @Test
    @Suppress("DEPRECATION")
    fun scheduleSnoozeUsesSnoozeRequestCode() {
        scheduler.scheduleSnooze(repeating, 60_000L)
        val alarms = alarmManager.scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(
            AlarmScheduler.requestCode(1, AlarmScheduler.ROLE_SNOOZE),
            shadowOf(alarms[0].operation).requestCode,
        )
    }

    @Test
    fun usesAlarmClockWhenExactAlarmsGranted() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        scheduler.schedule(repeating)
        assertNotNull(alarmManager.scheduledAlarms.single().alarmClockInfo)
    }

    @Test
    fun fallsBackToInexactWindowWhenExactNotGranted() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        scheduler.schedule(repeating)
        val alarm = alarmManager.scheduledAlarms.single()
        assertNull(alarm.alarmClockInfo)
        assertEquals(60_000L, alarm.windowLengthMs)
    }

    @Test
    fun canScheduleExactTrueWhenGranted() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(scheduler.canScheduleExact())
    }

    @Test
    fun canScheduleExactFalseWhenDenied() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(scheduler.canScheduleExact())
    }

    @Test
    @Config(sdk = [30])
    fun preSUsesAlarmClockRegardlessOfPermission() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        scheduler.schedule(repeating)
        assertNotNull(alarmManager.scheduledAlarms.single().alarmClockInfo)
    }

    @Test
    fun scheduleMonthlyCreatesAlarmWithMainRequestCode() {
        assertSchedulesMainAlarm(Alarm(1, 8, 0, monthlyDay = 12))
    }

    @Test
    fun scheduleDateAlarmCreatesAlarmWithMainRequestCode() {
        assertSchedulesMainAlarm(Alarm(1, 8, 0, dateMillis = tomorrowMidnight().timeInMillis))
    }

    @Test
    fun scheduleUsesNextTriggerTime() {
        val alarm = Alarm(1, 8, 0, dateMillis = tomorrowMidnight().timeInMillis)
        scheduler.schedule(alarm)
        assertEquals(
            scheduler.nextTrigger(alarm),
            alarmManager.scheduledAlarms.single().triggerAtMs,
        )
    }

    @Test
    fun scheduleSnoozeTriggersAtNowPlusDelay() {
        val before = android.os.SystemClock.elapsedRealtime()
        scheduler.scheduleSnooze(repeating, 60_000L)
        val after = android.os.SystemClock.elapsedRealtime()
        val snooze = alarmManager.scheduledAlarms.single()
        assertEquals(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, snooze.type)
        assertTrue(snooze.triggerAtMs >= before + 60_000L && snooze.triggerAtMs <= after + 60_000L)
    }

    @Test
    fun snoozeStillArmedWhenExactNotGranted() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        scheduler.scheduleSnooze(repeating, 60_000L)
        val snooze = alarmManager.scheduledAlarms.single()
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, snooze.type)
    }
}
