package com.malarm

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Debug-build only: the `debug_schedule` hook is gated on BuildConfig.DEBUG,
 * so these live in testDebug and do not run against the release variant.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DebugScheduleTest {

    private fun cleanState(): android.app.AlarmManager {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("malarm", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        kotlinx.coroutines.runBlocking { EventLog.clear(app) }
        val alarmManager = app.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        shadowOf(alarmManager).scheduledAlarms.toList().forEach {
            alarmManager.cancel(it.operation!!)
        }
        return alarmManager
    }

    private fun scheduledMainAlarms(alarmManager: android.app.AlarmManager) =
        shadowOf(alarmManager).scheduledAlarms.filter {
            shadowOf(it.operation).savedIntent?.action == AlarmScheduler.ACTION_ALARM
        }

    @Test
    fun debugScheduleDefaultsToOneMinuteProductionPath() {
        val alarmManager = cleanState()
        val app = RuntimeEnvironment.getApplication()

        Robolectric.buildActivity(
            MainActivity::class.java,
            Intent().putExtra("debug_schedule", true),
        ).setup()

        val alarm = AlarmStore(app).all().single { it.label == "Debug alarm" }
        val scheduled = scheduledMainAlarms(alarmManager).single()
        assertEquals(AlarmScheduler(app).nextTrigger(alarm), scheduled.triggerAtMs)
    }

    @Test
    fun debugScheduleSecsFiresInSeconds() {
        val alarmManager = cleanState()

        val before = System.currentTimeMillis()
        Robolectric.buildActivity(
            MainActivity::class.java,
            Intent().putExtra("debug_schedule", true).putExtra("debug_schedule_secs", 20),
        ).setup()
        val after = System.currentTimeMillis()

        val scheduled = scheduledMainAlarms(alarmManager).single()
        assertTrue(
            "expected trigger ~20 s out, was ${scheduled.triggerAtMs - before} ms out",
            scheduled.triggerAtMs in before + 20_000L..after + 20_000L,
        )
    }
}
