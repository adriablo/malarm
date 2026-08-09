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

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        scheduler = AlarmScheduler(context)
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
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
    fun cancelSnoozeRemovesSnoozeAlarm() {
        scheduler.scheduleSnooze(repeating, 60_000L)
        scheduler.cancelSnooze(repeating)
        assertTrue(alarmManager.scheduledAlarms.isEmpty())
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
        val before = System.currentTimeMillis()
        scheduler.scheduleSnooze(repeating, 60_000L)
        val after = System.currentTimeMillis()
        val trigger = alarmManager.scheduledAlarms.single().triggerAtMs
        assertTrue(trigger >= before + 60_000L && trigger <= after + 60_000L)
    }
}
