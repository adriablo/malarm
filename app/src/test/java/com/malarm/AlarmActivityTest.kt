package com.malarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    @Test
    fun backPressSnoozesAndFinishes() {
        val alarm = Alarm(1, 8, 0)
        store.save(alarm)
        val received = mutableListOf<Intent>()
        val probe = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                received.add(intent)
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            probe,
            IntentFilter(AlarmScheduler.ACTION_SNOOZE),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            val controller = Robolectric.buildActivity(
                AlarmActivity::class.java,
                AlarmActivity.intent(context, alarm.id),
            ).setup()
            controller.get().onBackPressedDispatcher.onBackPressed()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(controller.get().isFinishing)
            val snooze = received.singleOrNull { it.action == AlarmScheduler.ACTION_SNOOZE }
            assertNotNull("expected a snooze broadcast on back press", snooze)
            assertEquals(1L, snooze!!.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1))
        } finally {
            context.unregisterReceiver(probe)
        }
    }

    @Test
    fun missingAlarmFinishesImmediately() {
        val controller = Robolectric.buildActivity(
            AlarmActivity::class.java,
            AlarmActivity.intent(context, 999L),
        ).setup()
        assertTrue(controller.get().isFinishing)
    }

    private fun probeFor(action: String, block: (MutableList<Intent>) -> Unit): List<Intent> {
        val received = mutableListOf<Intent>()
        val probe = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                received.add(intent)
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            probe,
            IntentFilter(action),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            block(received)
        } finally {
            context.unregisterReceiver(probe)
        }
        return received
    }

    @Test
    fun snoozeButtonSendsBroadcastAndFinishes() {
        // Manual 5.2 logic: full-screen Snooze broadcasts ACTION_SNOOZE.
        store.save(Alarm(1, 8, 0))
        probeFor(AlarmScheduler.ACTION_SNOOZE) { received ->
            val controller = Robolectric.buildActivity(
                AlarmActivity::class.java,
                AlarmActivity.intent(context, 1L),
            ).setup()
            controller.get().findViewById<android.widget.Button>(R.id.snooze).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(controller.get().isFinishing)
            val snooze = received.singleOrNull { it.action == AlarmScheduler.ACTION_SNOOZE }
            assertNotNull("expected a snooze broadcast on Snooze press", snooze)
            assertEquals(1L, snooze!!.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1))
        }
    }

    @Test
    fun dismissButtonSendsBroadcastAndFinishes() {
        // Manual 5.4 logic: full-screen Dismiss broadcasts ACTION_DISMISS.
        store.save(Alarm(1, 8, 0))
        probeFor(AlarmScheduler.ACTION_DISMISS) { received ->
            val controller = Robolectric.buildActivity(
                AlarmActivity::class.java,
                AlarmActivity.intent(context, 1L),
            ).setup()
            controller.get().findViewById<android.widget.Button>(R.id.dismiss).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(controller.get().isFinishing)
            val dismiss = received.singleOrNull { it.action == AlarmScheduler.ACTION_DISMISS }
            assertNotNull("expected a dismiss broadcast on Dismiss press", dismiss)
            assertEquals(1L, dismiss!!.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1))
        }
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
