package com.malarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class BootReceiverTest {

    private lateinit var context: Context
    private lateinit var store: AlarmStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        store = AlarmStore(context)
        context.getSharedPreferences("malarm", Context.MODE_PRIVATE).edit().clear().commit()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowOf(alarmManager).scheduledAlarms.forEach { alarmManager.cancel(it.operation!!) }
    }

    private fun sendBoot() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private val scheduledAlarms
        get() = shadowOf(
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager,
        ).scheduledAlarms.filter {
            shadowOf(it.operation).savedIntent?.action == AlarmScheduler.ACTION_ALARM
        }

    @Test
    fun ignoresNonBootActions() {
        store.save(Alarm(1, 8, 0, repeatDays = setOf(Calendar.MONDAY)))
        BootReceiver().onReceive(context, Intent("com.malarm.ACTION_SOMETHING_ELSE"))
        assertTrue(scheduledAlarms.isEmpty())
    }

    @Test
    fun reschedulesEnabledAlarmsOnBoot() {
        store.save(Alarm(1, 8, 0, repeatDays = setOf(Calendar.MONDAY)))
        sendBoot()
        assertEquals(1, scheduledAlarms.size)
    }

    @Test
    fun doesNotScheduleDisabledAlarmsOnBoot() {
        store.save(Alarm(1, 8, 0, repeatDays = setOf(Calendar.MONDAY), enabled = false))
        sendBoot()
        assertTrue(scheduledAlarms.isEmpty())
    }

    @Test
    fun schedulesAllEnabledAlarmsOnBoot() {
        store.save(Alarm(1, 8, 0, repeatDays = setOf(Calendar.MONDAY)))
        store.save(Alarm(2, 9, 0, repeatDays = setOf(Calendar.TUESDAY)))
        store.save(Alarm(3, 10, 0, repeatDays = setOf(Calendar.WEDNESDAY), enabled = false))
        sendBoot()
        assertEquals(2, scheduledAlarms.size)
    }
}
