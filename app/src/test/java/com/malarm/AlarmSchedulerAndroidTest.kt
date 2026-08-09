package com.malarm

import android.app.AlarmManager
import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Test
    fun scheduleCreatesSingleAlarmWithMainRequestCode() {
        scheduler.schedule(repeating)
        val alarms = alarmManager.scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(
            AlarmScheduler.requestCode(1, AlarmScheduler.ROLE_MAIN),
            shadowOf(alarms[0].operation).requestCode,
        )
    }

    @Test
    fun scheduleDoesNothingForExpiredOneShot() {
        scheduler.schedule(Alarm(1, 1, 0))
        assertTrue(alarmManager.scheduledAlarms.isEmpty())
    }

    @Test
    fun cancelRemovesScheduledAlarms() {
        scheduler.schedule(repeating)
        scheduler.cancel(repeating)
        assertTrue(alarmManager.scheduledAlarms.isEmpty())
    }

    @Test
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
}
