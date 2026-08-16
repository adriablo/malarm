package com.malarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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
    fun snoozeActionSchedulesSnoozeAndStopsRinging() {
        store.save(Alarm(1, 8, 0))
        receive(Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_SNOOZE
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, 1L)
        })
        assertEquals(1, scheduledAlarms.size)
        assertFalse(channelExists())
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

    private fun rescheduleAll() {
        receive(Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_RESCHEDULE_ALL
        })
    }

    @Test
    fun rescheduleAllIsNoOpWhenTimezoneUnchanged() {
        store.save(Alarm(1, 8, 0, repeatDays = setOf(Calendar.MONDAY)))
        store.setTimeZoneId(TimeZone.getDefault().id)
        rescheduleAll()
        assertTrue(scheduledAlarms.isEmpty())
    }

    @Test
    fun rescheduleAllReArmsAlarmsWhenTimezoneChanged() {
        store.save(Alarm(1, 8, 0, repeatDays = setOf(Calendar.MONDAY)))
        store.setTimeZoneId("not/current")
        rescheduleAll()
        assertEquals(1, scheduledAlarms.size)
        assertEquals(TimeZone.getDefault().id, store.timeZoneId())
    }
}
