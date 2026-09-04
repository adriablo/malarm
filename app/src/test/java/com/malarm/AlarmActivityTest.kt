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
}
